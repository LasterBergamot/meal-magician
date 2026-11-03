#!/usr/bin/env bash
# Installs the `age` encryption tool (https://github.com/FiloSottile/age) into $HOME/.local/bin, for
# the nightly backup (db-backup.yml, #62). The GitHub runner does not ship it, and the package
# manager's copy is too old to trust.
#
# The version and the SHA-256 of the linux/amd64 tarball are pinned here: a download that doesn't
# match is deleted, never unpacked. To bump: set AGE_VERSION, then copy the hash from the release
# page https://github.com/FiloSottile/age/releases (the asset's "sha256:" line), or from
#   gh api repos/FiloSottile/age/releases/tags/<version> --jq '.assets[] | [.name, .digest] | @tsv'
# (Dependabot can't track this file, so a bump is a manual chore.)
# Only linux/amd64 is supported: that is the GitHub runner. On your own machine use your package
# manager; the restore guide only needs a recent `age`.
set -euo pipefail

AGE_VERSION="v1.3.2"
AGE_SHA256="cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10"
# Source: https://github.com/FiloSottile/age/releases/tag/v1.3.2 (age-v1.3.2-linux-amd64.tar.gz)

if [[ "$(uname -s)-$(uname -m)" != "Linux-x86_64" ]]; then
  echo "install-age.sh supports linux/amd64 only (this is $(uname -s) $(uname -m))." >&2
  exit 1
fi

bin_dir="${AGE_BIN_DIR:-$HOME/.local/bin}"
mkdir -p "$bin_dir"

if [[ -x "$bin_dir/age" ]] && "$bin_dir/age" --version 2>/dev/null | grep -q "${AGE_VERSION#v}"; then
  echo "age $AGE_VERSION already in $bin_dir"
  exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

echo "Downloading age $AGE_VERSION…"
curl -fsSL --retry 3 -o "$work/age.tar.gz" \
  "https://github.com/FiloSottile/age/releases/download/$AGE_VERSION/age-$AGE_VERSION-linux-amd64.tar.gz"

actual=$(sha256sum "$work/age.tar.gz" | cut -d' ' -f1)
if [[ $actual != "$AGE_SHA256" ]]; then
  echo "Checksum mismatch for age $AGE_VERSION: expected $AGE_SHA256, got $actual. Not installing." >&2
  exit 1
fi

tar -xzf "$work/age.tar.gz" -C "$work"
install -m 0755 "$work/age/age" "$work/age/age-keygen" "$bin_dir/"
echo "Installed age $AGE_VERSION in $bin_dir"
