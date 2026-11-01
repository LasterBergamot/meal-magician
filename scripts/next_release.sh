#!/usr/bin/env bash
# Decides whether a commit on `main` has to be released and with which version
# (.github/workflows/release.yml, issue #51).
#
# It prints `release=true|false`, `version=X.Y.Z` and `reason=...`, and appends the same lines to
# the file in GITHUB_OUTPUT when that is set. It needs the full history and the tags (checkout
# with `fetch-depth: 0`).
#
# Rules:
# - A manual run with an explicit version releases exactly that version (the tag must not exist).
# - Otherwise the version is the latest `vX.Y.Z` tag plus one patch step. The label
#   `release:major` or `release:minor` on the merged PR (or the dispatch input `bump`) raises the
#   major or minor part instead and resets the lower ones.
# - An automatic run (EVENT_NAME=workflow_run) releases nothing when
#   * the commit is not newer than the last released commit (the tag's parent), so a late run can
#     never deploy older code over newer code, or
#   * every file changed since the last released commit is documentation or CI configuration that
#     the image does not contain: Markdown, documentation/, .claude/ and .github/. Anything else
#     counts as code and fails open to "release". The comparison starts at the last *released*
#     commit, not at the previous merge, so a docs-only merge still ships earlier code whose
#     deploy failed or was skipped.
# - A manual run without a version always releases the computed version.
#
# Inputs (environment): EVENT_NAME (`workflow_run` or `workflow_dispatch`), SHA (the commit to
# release), LABELS (labels of the merged PR, one per line or comma separated, may be empty),
# VERSION_INPUT (manual only, may be empty) and BUMP_INPUT (manual only: patch, minor or major).
# Run it locally the same way, for example
#   EVENT_NAME=workflow_run SHA=$(git rev-parse HEAD) LABELS=release:minor bash scripts/next_release.sh
set -euo pipefail

: "${EVENT_NAME:?EVENT_NAME is required}"
: "${SHA:?SHA is required}"
LABELS="${LABELS:-}"
VERSION_INPUT="${VERSION_INPUT:-}"
BUMP_INPUT="${BUMP_INPUT:-patch}"

emit() {
  local release="$1" version="$2" reason="$3"
  echo "release=$release"
  echo "version=$version"
  echo "reason=$reason"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "release=$release"
      echo "version=$version"
      echo "reason=$reason"
    } >>"$GITHUB_OUTPUT"
  fi
  exit 0
}

tag_exists() {
  git rev-parse --quiet --verify "refs/tags/v$1" >/dev/null
}

if [[ "$EVENT_NAME" == "workflow_dispatch" && -n "$VERSION_INPUT" ]]; then
  if ! [[ "$VERSION_INPUT" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Version must look like 0.1.0 (digits only, no v), got '$VERSION_INPUT'" >&2
    exit 1
  fi
  if tag_exists "$VERSION_INPUT"; then
    echo "Tag v$VERSION_INPUT already exists; pick a new version" >&2
    exit 1
  fi
  emit true "$VERSION_INPUT" "Manual release of the version that was typed in."
fi

latest_tag=$(git tag --list 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -n 1)

bump=patch
if [[ "$EVENT_NAME" == "workflow_dispatch" ]]; then
  bump="$BUMP_INPUT"
elif grep -Fxq 'release:major' <<<"${LABELS//,/$'\n'}"; then
  bump=major
elif grep -Fxq 'release:minor' <<<"${LABELS//,/$'\n'}"; then
  bump=minor
fi

if [[ -z "$latest_tag" ]]; then
  # Nothing released yet: the first release is 0.1.0 whatever the bump.
  emit true "0.1.0" "No release tag exists yet."
fi

if [[ "$EVENT_NAME" == "workflow_run" ]]; then
  # The tag sits on a detached commit that only bumps the version; its parent is the released main commit.
  released=$(git rev-parse "$latest_tag^")
  if [[ "$released" == "$SHA" ]]; then
    emit false "" "$SHA is already released as $latest_tag."
  fi
  if ! git merge-base --is-ancestor "$released" "$SHA"; then
    emit false "" "$SHA is not newer than the last released commit ($latest_tag); deploying it would go back in time."
  fi
  only_docs=true
  while IFS= read -r file; do
    case "$file" in
      *.md | documentation/* | .claude/* | .github/*) ;;
      *) only_docs=false; break ;;
    esac
  done < <(git diff --name-only "$released" "$SHA")
  if [[ "$only_docs" == true ]]; then
    emit false "" "Only documentation or CI configuration changed since $latest_tag: nothing to deploy."
  fi
fi

IFS=. read -r major minor patch <<<"${latest_tag#v}"
case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  patch) patch=$((patch + 1)) ;;
  *)
    echo "BUMP_INPUT must be patch, minor or major, got '$bump'" >&2
    exit 1
    ;;
esac
version="$major.$minor.$patch"
if tag_exists "$version"; then
  echo "Computed version v$version already exists" >&2
  exit 1
fi
emit true "$version" "$bump release after $latest_tag."
