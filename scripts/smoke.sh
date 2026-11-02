#!/usr/bin/env bash
# Post-deploy smoke test (`task smoke -- <base-url>`, issue #58).
#
# Fetches a handful of PUBLIC pages from a running Meal Magician and checks that they look the way
# the app really serves them. It never signs in, sends no credentials or cookies and reads no
# personal data; the one protected path it requests is only there to confirm that an anonymous
# visitor is turned away. `release.yml` runs it once per release, after the health poll and before
# the version tag is pushed, against the Cloud Run URL. It is never run on a timer (PLAN.md D11),
# and the PR `E2E` job already covers the built image, so CI does not call it for pull requests.
#
# Checks (each prints PASS or FAIL with the reason):
#  - health:     /actuator/health answers 200 with "status":"UP"
#  - login:      / answers 200 after the redirect and is the login page (the Google sign-in link)
#  - protected:  /households redirects to /login instead of answering 200
#  - headers:    the CSP (no unsafe-inline / unsafe-eval, frame-ancestors 'none'),
#                X-Content-Type-Options, Referrer-Policy and X-Frame-Options on the login page and on
#                the redirect, plus Strict-Transport-Security when the URL is https
#  - static:     /css/app.css, /manifest.webmanifest and /sw.js answer 200 with a body
#  - no 5xx:     none of the requests above got a 5xx answer
#
# The service may be cold (Cloud Run scales to zero, Neon suspends), so only the first request, the
# health check, is retried for a short while. Everything else must work at the first attempt, so a
# flaky page is reported instead of hidden.
#
# Usage: scripts/smoke.sh <base-url>        e.g. scripts/smoke.sh http://localhost:18080
# Environment (optional): SMOKE_WARMUP_ATTEMPTS (default 6) and SMOKE_WARMUP_PAUSE seconds (default 10)
# for the health retries, SMOKE_TIMEOUT seconds per request (default 30).
# Exit status: 0 when every check passed, 1 when any failed, 2 for a missing or invalid URL.
set -euo pipefail

if [[ $# -ne 1 || ! $1 =~ ^https?://[^/[:space:]]+(/.*)?$ ]]; then
  echo "usage: $0 <base-url>   (for example http://localhost:18080 or https://meal-magician-123.europe-west3.run.app)" >&2
  exit 2
fi
base=${1%/}

attempts=${SMOKE_WARMUP_ATTEMPTS:-6}
pause=${SMOKE_WARMUP_PAUSE:-10}
timeout=${SMOKE_TIMEOUT:-30}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

failed=0
statuses=()

pass() { printf 'PASS  %-10s %s\n' "$1" "$2"; }
fail() {
  printf 'FAIL  %-10s %s\n' "$1" "$2"
  failed=1
}

# fetch NAME PATH [curl options...]: GET base+PATH without cookies or credentials. Leaves the
# status in $status, the final URL in $final_url, the response headers (CR removed) in
# $work/NAME.headers and the body in $work/NAME.body. A connection error or timeout gives status 000.
fetch() {
  local name=$1 path=$2
  shift 2
  local out
  out=$(curl --silent --show-error --max-time "$timeout" --output "$work/$name.body" \
    --dump-header "$work/$name.headers" --write-out '%{http_code} %{url_effective}' \
    "$@" "$base$path" 2>"$work/$name.err") || true
  status=${out%% *}
  final_url=${out#* }
  [[ -n $status ]] || status=000
  statuses+=("$status")
  tr -d '\r' <"$work/$name.headers" >"$work/$name.headers.clean" 2>/dev/null || true
  mv "$work/$name.headers.clean" "$work/$name.headers"
}

# header FILE NAME: the value of the (last) response header NAME in FILE, empty when absent.
header() {
  local value
  value=$(grep -i "^$2:" "$1" | tail -n 1 || true)
  value=${value#*:}
  # trim the space after the colon and any trailing blanks
  value=${value#"${value%%[![:space:]]*}"}
  printf '%s' "${value%"${value##*[![:space:]]}"}"
}

# check_headers LABEL FILE: the security headers the app sends on every response, even redirects.
check_headers() {
  local label=$1 file=$2 problems=() csp value
  csp=$(header "$file" Content-Security-Policy)
  if [[ -z $csp ]]; then
    problems+=("Content-Security-Policy is missing")
  else
    [[ $csp == *"default-src 'self'"* ]] || problems+=("CSP has no default-src 'self'")
    [[ $csp == *"frame-ancestors 'none'"* ]] || problems+=("CSP has no frame-ancestors 'none'")
    [[ $csp != *unsafe-inline* ]] || problems+=("CSP allows unsafe-inline")
    [[ $csp != *unsafe-eval* ]] || problems+=("CSP allows unsafe-eval")
  fi
  value=$(header "$file" X-Content-Type-Options)
  [[ ${value,,} == nosniff ]] || problems+=("X-Content-Type-Options is '${value:-missing}', want nosniff")
  value=$(header "$file" Referrer-Policy)
  [[ ${value,,} == same-origin ]] || problems+=("Referrer-Policy is '${value:-missing}', want same-origin")
  value=$(header "$file" X-Frame-Options)
  [[ ${value^^} == DENY ]] || problems+=("X-Frame-Options is '${value:-missing}', want DENY")
  if [[ $base == https://* ]]; then
    value=$(header "$file" Strict-Transport-Security)
    [[ $value == *max-age=* ]] || problems+=("Strict-Transport-Security is missing on https")
  fi
  if ((${#problems[@]} == 0)); then
    pass headers "$label carries the CSP and the other security headers"
  else
    local joined
    joined=$(printf '%s; ' "${problems[@]}")
    fail headers "$label: ${joined%; }"
  fi
}

echo "Smoke test against $base"

# --- health (retried while the service wakes up) ---------------------------------------------
health_ok=0
for ((attempt = 1; attempt <= attempts; attempt++)); do
  fetch health /actuator/health
  if [[ $status == 200 ]] && grep -q '"status" *: *"UP"' "$work/health.body"; then
    health_ok=1
    break
  fi
  # A 5xx or a refused connection during warm-up is expected: forget this answer, try again.
  if ((attempt < attempts)); then
    unset 'statuses[-1]'
    sleep "$pause"
  fi
done
if ((health_ok)); then
  pass health "/actuator/health is UP"
else
  detail=$(head -c 200 "$work/health.body" 2>/dev/null | tr '\n' ' ' || true)
  err=$(head -c 200 "$work/health.err" 2>/dev/null | tr '\n' ' ' || true)
  fail health "/actuator/health gave status $status after $attempts tries, want 200 with \"status\":\"UP\" (${detail:-${err:-no body}})"
fi

# --- login page: "/" follows the redirect for anonymous visitors -------------------------------
fetch home / --location --max-redirs 5
if [[ $status != 200 ]]; then
  fail login "/ ended with status $status at $final_url, want 200"
elif [[ ${final_url#*://*/} != login && ${final_url#*://*/} != login\?* ]]; then
  # Only the path counts: behind a proxy the redirect may name the service by another address.
  fail login "/ ended at $final_url, want the /login page"
elif ! grep -q '/oauth2/authorization/google' "$work/home.body"; then
  fail login "/ answered 200 but the page has no Google sign-in link, so it is not the login page"
else
  pass login "/ leads to the login page (200, Google sign-in link present)"
fi

# --- protected path: an anonymous visitor must be redirected to sign-in ------------------------
fetch protected /households
location=$(header "$work/protected.headers" Location)
if [[ $status != 3?? ]]; then
  fail protected "/households answered $status, want a redirect to /login (200 would mean it is open to anyone)"
elif [[ $location != "/login" && $location != "$base/login" && $location != */login ]]; then
  fail protected "/households redirects to '${location:-nowhere}', want /login"
else
  pass protected "/households redirects anonymous visitors to $location ($status)"
fi
if [[ $status == 3?? ]]; then
  check_headers "the redirect from /households" "$work/protected.headers"
fi

# --- security headers on the login page itself -------------------------------------------------
fetch loginpage /login
if [[ $status == 200 ]]; then
  check_headers "/login" "$work/loginpage.headers"
else
  fail headers "/login answered $status, want 200, so its headers were not checked"
fi

# --- static files: the stylesheet, the PWA manifest and the service worker ---------------------
for path in /css/app.css /manifest.webmanifest /sw.js; do
  fetch static "$path"
  if [[ $status != 200 ]]; then
    fail static "$path answered $status, want 200"
  elif [[ ! -s $work/static.body ]]; then
    fail static "$path answered 200 with an empty body"
  else
    pass static "$path loads (200, $(wc -c <"$work/static.body" | tr -d ' ') bytes)"
  fi
done

# --- no 5xx anywhere ---------------------------------------------------------------------------
# Warm-up attempts of the health check that were retried are not in the list (only the last answer
# of each page is). A connection error or timeout (000) is not a 5xx; its own check fails instead.
server_errors=()
for code in "${statuses[@]}"; do
  [[ $code == 5?? ]] && server_errors+=("$code")
done
if ((${#server_errors[@]} == 0)); then
  pass "no 5xx" "none of ${#statuses[@]} requests got a server error"
else
  fail "no 5xx" "${#server_errors[@]} request(s) got a server error: ${server_errors[*]}"
fi

if ((failed)); then
  echo "Smoke test FAILED" >&2
  exit 1
fi
echo "Smoke test passed"
