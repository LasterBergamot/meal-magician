#!/usr/bin/env bash
# `task serve` / `task serve:pg`: runs the Tailwind watcher next to the app, and stops both
# together. Usage: scripts/serve.sh <spring profiles>, e.g. `scripts/serve.sh dev`.
#
# Why a script and not two Taskfile commands: a background process must be killed when the app
# stops (Ctrl-C), or it would keep running and rebuilding CSS for nobody. `trap ... EXIT` does
# that reliably in bash.
#
# Why -PskipTailwind: the watcher already builds the stylesheet, so Gradle must not build it a
# second time into the same file. The dev profile reads static files straight from
# src/main/resources/static (application-dev.yml), so a CSS or JS change shows up on reload
# without restarting the app.
set -euo pipefail

profiles="${1:?usage: scripts/serve.sh <spring profiles>}"
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -x .tools/tailwindcss ]]; then
  echo "The Tailwind CLI is missing (.tools/tailwindcss). Run 'task install' once." >&2
  exit 1
fi

# --watch=always: the plain --watch stops when stdin is closed, which is the case in a background job.
.tools/tailwindcss --input src/main/css/app.css --output src/main/resources/static/css/app.css --watch=always &
tailwind_pid=$!
trap 'kill "$tailwind_pid" 2>/dev/null || true' EXIT

./gradlew bootRun -PskipTailwind --args="--spring.profiles.active=$profiles"
