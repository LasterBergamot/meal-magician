#!/usr/bin/env bash
# Decides whether the E2E job has to run (.github/workflows/ci.yml, issue #37).
#
# The job always starts and always reports a result, so the required check "E2E" is never left
# pending or skipped; this script only tells its expensive steps (image build, Playwright, Trivy)
# whether to run. It prints `run=true` or `run=false` and appends the same line to the file in
# GITHUB_OUTPUT when that is set.
#
# `run=false` only when this is a pull request and EVERY changed file is documentation that no
# build, image or test reads: Markdown outside src/, documentation/ and .claude/. Anything else,
# a push to main, an empty diff or an unknown event runs E2E (it fails open to "run": a wasted
# two minutes is cheaper than an unchecked image). Changes to ci.yml, the Dockerfile and the
# compose files are not documentation, so they always run it.
#
# Inputs (environment): EVENT_NAME, BASE_SHA, HEAD_SHA (the last two only for pull requests).
# Run it locally the same way, e.g.
#   EVENT_NAME=pull_request BASE_SHA=origin/main HEAD_SHA=HEAD bash scripts/e2e_needed.sh
set -euo pipefail

: "${EVENT_NAME:?EVENT_NAME is required}"

emit() {
  echo "run=$1"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "run=$1" >>"$GITHUB_OUTPUT"
  fi
  echo "$2" >&2
  exit 0
}

if [[ "$EVENT_NAME" != "pull_request" ]]; then
  emit true "Not a pull request ($EVENT_NAME): E2E runs."
fi

: "${BASE_SHA:?BASE_SHA is required}"
: "${HEAD_SHA:?HEAD_SHA is required}"

mapfile -t changed < <(git diff --name-only "$BASE_SHA...$HEAD_SHA")
if [[ ${#changed[@]} -eq 0 ]]; then
  emit true "No changed files found: E2E runs."
fi

for file in "${changed[@]}"; do
  case "$file" in
    src/*) emit true "$file can reach the app: E2E runs." ;;
    *.md | documentation/* | .claude/*) ;;
    *) emit true "$file is not documentation: E2E runs." ;;
  esac
done

emit false "Only documentation changed (${#changed[@]} files): E2E is skipped, the check still passes."
