#!/usr/bin/env bash
# Copies documentation/*.md into the GitHub wiki repo (.github/workflows/wiki-sync.yml).
#
# Inputs (environment):
#   PAGE_MAP    lines of `<file in documentation/>|<wiki page title>` (lives in wiki-sync.yml)
#   WIKI_URL    the wiki's git URL; defaults to https://github.com/$REPO.wiki.git
#   REPO        owner/name, only used for the default WIKI_URL
#   WIKI_TOKEN  token for HTTPS pushes; omit for a local URL (this is how the script is tested)
#   DOCS_DIR    the source folder; defaults to documentation
#
# The wiki mirrors the folder exactly: pages that are no longer in PAGE_MAP are deleted, so the
# wiki can never drift from `main`. It fails (before touching the wiki) when a file in
# documentation/ is missing from PAGE_MAP, or the map names a file that does not exist.
set -euo pipefail

: "${PAGE_MAP:?PAGE_MAP is required}"
DOCS_DIR="${DOCS_DIR:-documentation}"
WIKI_URL="${WIKI_URL:-https://github.com/${REPO:?REPO or WIKI_URL is required}.wiki.git}"

declare -A title_of=()
while IFS='|' read -r file title; do
  [[ -z "${file// /}" ]] && continue
  [[ -f "$DOCS_DIR/$file" ]] || { echo "PAGE_MAP names $file but $DOCS_DIR/$file does not exist." >&2; exit 1; }
  title_of["$file"]="$title"
done <<<"$PAGE_MAP"

missing=0
for path in "$DOCS_DIR"/*.md; do
  file="$(basename "$path")"
  if [[ -z "${title_of[$file]+x}" ]]; then
    echo "$DOCS_DIR/$file is not in the page map (.github/workflows/wiki-sync.yml)." >&2
    missing=1
  fi
done
[[ "$missing" == 0 ]] || exit 1

git_wiki=(git)
if [[ -n "${WIKI_TOKEN:-}" ]]; then
  # The token goes in a header, not the URL, so it never appears in a log or in .git/config.
  basic="$(printf 'x-access-token:%s' "$WIKI_TOKEN" | base64 -w0)"
  echo "::add-mask::$basic"
  git_wiki=(git -c "http.extraheader=Authorization: Basic $basic")
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
if ! "${git_wiki[@]}" clone --quiet "$WIKI_URL" "$work/wiki"; then
  echo "Could not clone the wiki. Create its first page once in the web UI (Wiki > Create the first page); that creates the git repo this script pushes to." >&2
  exit 1
fi

find "$work/wiki" -maxdepth 1 -type f -name '*.md' -delete
for file in "${!title_of[@]}"; do
  title="${title_of[$file]}"
  cp "$DOCS_DIR/$file" "$work/wiki/${title// /-}.md"
done

cd "$work/wiki"
git add --all
if git diff --cached --quiet; then
  echo "Wiki already up to date."
  exit 0
fi
git -c user.name="github-actions[bot]" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit --quiet -m "Sync documentation/ from ${GITHUB_SHA:-local}"
"${git_wiki[@]}" push --quiet origin HEAD
echo "Wiki updated."
