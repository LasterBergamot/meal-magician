#!/usr/bin/env bash
# Checks that the tools this repo needs are installed and new enough (`task doctor`).
#
# Required tools fail the run; optional ones are only reported. Version floors:
#  - Java 17+: only has to run the Gradle wrapper. The Gradle toolchain downloads Temurin 25 for
#    compiling and testing (PLAN.md D21), so the machine's JDK does not need to be 25.
#  - Docker Compose 2.24+: compose files may use newer syntax such as `env_file` with `required`.
# Exit status: 0 when every required tool is present and new enough, 1 otherwise.
set -uo pipefail

failed=0

# version_ge A B: true when dotted version A >= B (numeric compare per field).
version_ge() {
  local IFS=.
  local -a a=($1) b=($2)
  local i
  for ((i = 0; i < ${#b[@]}; i++)); do
    local x=${a[i]:-0} y=${b[i]:-0}
    ((10#$x > 10#$y)) && return 0
    ((10#$x < 10#$y)) && return 1
  done
  return 0
}

# first_version TEXT: the first version number in TEXT (3.54.0, or just 27 for a bare major).
first_version() {
  grep -oE '[0-9]+(\.[0-9]+)*' <<<"$1" | head -n1
}

# java_version_line: the `version "…"` line of `java -version`. It skips any "Picked up
# JAVA_TOOL_OPTIONS" banner, whose flags contain numbers that would be mistaken for a version.
java_version_line() {
  java -version 2>&1 | grep -m1 ' version "'
}

# check KIND NAME MIN CMD...: run CMD, extract a version, compare with MIN (empty = any).
# KIND is `required` (a miss fails the run) or `optional` (only reported).
check() {
  local kind=$1 name=$2 min=$3
  shift 3
  local out version
  if ! out=$("$@" 2>&1); then
    if [[ $kind == required ]]; then
      printf 'FAIL  %-15s not found (required)\n' "$name"
      failed=1
    else
      printf 'skip  %-15s not found (optional)\n' "$name"
    fi
    return
  fi
  version=$(first_version "$out")
  if [[ -n $min ]] && ! version_ge "${version:-0}" "$min"; then
    if [[ $kind == required ]]; then
      printf 'FAIL  %-15s %s is older than %s\n' "$name" "${version:-unknown}" "$min"
      failed=1
    else
      printf 'old   %-15s %s is older than %s (optional)\n' "$name" "${version:-unknown}" "$min"
    fi
    return
  fi
  printf 'ok    %-15s %s\n' "$name" "${version:-present}"
}

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

echo "Required:"
check required java 17 java_version_line
# Arch Linux packages Task as `go-task` (usually aliased to `task`, but aliases don't reach scripts).
task_bin=$(command -v task || command -v go-task || echo task)
check required task 3 "$task_bin" --version
check required docker "" docker --version
# Testcontainers and compose need a running daemon, not just the client binary.
check required "docker daemon" "" docker info --format '{{.ServerVersion}}'
check required "docker compose" 2.24 docker compose version

echo "Optional:"
check optional gh 2 gh --version
check optional gcloud "" gcloud --version
check optional apm "" apm --version

# Installed by `task install`; reported here so a missing one is easy to spot.
if [[ -x .tools/tailwindcss ]]; then
  printf 'ok    %-15s %s\n' "tailwindcss" "$(cat .tools/tailwindcss.version 2>/dev/null || echo present)"
else
  printf 'skip  %-15s not installed (run `task install`)\n' "tailwindcss"
fi

if ((failed)); then
  echo "Missing or outdated required tools. Install links: README.md, section Prerequisites."
  exit 1
fi
echo "All required tools are present."
