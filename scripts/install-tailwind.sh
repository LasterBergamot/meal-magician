#!/usr/bin/env bash
# Downloads the Tailwind CSS standalone CLI into .tools/ (`task install`), so the project needs
# no Node toolchain (PLAN.md D4).
#
# The version and the SHA-256 of each platform's binary are pinned here: a binary that doesn't
# match is deleted, never run. To bump: set TAILWIND_VERSION, then copy the four hashes from
#   https://github.com/tailwindlabs/tailwindcss/releases/download/<version>/sha256sums.txt
# (Dependabot can't track this file, so a bump is a manual chore.)
# Re-running is cheap: nothing is downloaded when .tools/ already holds the pinned version.
set -euo pipefail

TAILWIND_VERSION="v4.3.3"

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) asset=tailwindcss-linux-x64 sha256=dc61b3ac6b8c9ca874c0cc4c57b2409791a64c5540404ca5f5367360babc313a ;;
  Linux-aarch64) asset=tailwindcss-linux-arm64 sha256=55fd0b241214eff3de1e8ee4f22796662f2d2e7a49bcfca7477cfd0bac398195 ;;
  Darwin-x86_64) asset=tailwindcss-macos-x64 sha256=7922e0953f2110c05976e3bf58f14e643d90427575e766b7d433f5f80cbee7e1 ;;
  Darwin-arm64) asset=tailwindcss-macos-arm64 sha256=cdf646702987a743464dff4d9c60fd4480d1c1e73dd819a9a67f1078815dce9d ;;
  *)
    echo "No Tailwind standalone CLI for $(uname -s) $(uname -m) (Windows: use WSL)." >&2
    exit 1
    ;;
esac

cd "$(dirname "${BASH_SOURCE[0]}")/.."
mkdir -p .tools

if [[ -x .tools/tailwindcss && "$(cat .tools/tailwindcss.version 2>/dev/null)" == "$TAILWIND_VERSION" ]]; then
  echo "Tailwind CLI $TAILWIND_VERSION already in .tools/"
  exit 0
fi

tmp=$(mktemp .tools/tailwindcss.XXXXXX)
trap 'rm -f "$tmp"' EXIT

echo "Downloading Tailwind CLI $TAILWIND_VERSION ($asset)…"
curl -fsSL --retry 3 -o "$tmp" \
  "https://github.com/tailwindlabs/tailwindcss/releases/download/$TAILWIND_VERSION/$asset"

if command -v sha256sum >/dev/null; then
  actual=$(sha256sum "$tmp" | cut -d' ' -f1)
else
  actual=$(shasum -a 256 "$tmp" | cut -d' ' -f1)
fi
if [[ $actual != "$sha256" ]]; then
  echo "Checksum mismatch for $asset: expected $sha256, got $actual. Not installing." >&2
  exit 1
fi

chmod +x "$tmp"
mv "$tmp" .tools/tailwindcss
echo "$TAILWIND_VERSION" >.tools/tailwindcss.version
echo "Installed .tools/tailwindcss ($TAILWIND_VERSION)"
