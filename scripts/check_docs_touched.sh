#!/usr/bin/env bash
# The "Docs updated" PR check (.github/workflows/docs-check.yml; rule in CLAUDE.md).
#
# A PR passes when ANY of these holds:
#   * it changes a doc: README.md, CLAUDE.md, anything under documentation/, .claude/rules/ or
#     .claude/context/;
#   * it changes nothing but Markdown (a docs-only PR, e.g. PLAN.md bookkeeping);
#   * it carries the `no-docs` label AND a `No doc impact: <reason>` line in the body;
#   * it was opened by Dependabot, which cannot write docs.
# PLAN.md alone does not count as a doc change next to code: every PR ticks it, so counting it
# would make the check meaningless.
#
# Inputs (environment): BASE_SHA, HEAD_SHA, LABELS (comma-separated), PR_BODY, PR_AUTHOR.
# Run it locally the same way, e.g.
#   BASE_SHA=origin/main HEAD_SHA=HEAD LABELS= PR_BODY= PR_AUTHOR=me bash scripts/check_docs_touched.sh
set -euo pipefail

: "${BASE_SHA:?BASE_SHA is required}"
: "${HEAD_SHA:?HEAD_SHA is required}"
LABELS="${LABELS:-}"
PR_BODY="${PR_BODY:-}"
PR_AUTHOR="${PR_AUTHOR:-}"

if [[ "$PR_AUTHOR" == "dependabot[bot]" ]]; then
  echo "Dependabot PR: no docs expected."
  exit 0
fi

mapfile -t changed < <(git diff --name-only "$BASE_SHA...$HEAD_SHA")
if [[ ${#changed[@]} -eq 0 ]]; then
  echo "No changed files."
  exit 0
fi

only_markdown=true
for file in "${changed[@]}"; do
  case "$file" in
    README.md | CLAUDE.md | documentation/* | .claude/rules/* | .claude/context/*)
      echo "Docs touched: $file"
      exit 0
      ;;
    *.md) ;;
    *) only_markdown=false ;;
  esac
done

if [[ "$only_markdown" == true ]]; then
  echo "Only Markdown files changed: nothing more to document."
  exit 0
fi

has_label=false
case ",${LABELS}," in *,no-docs,*) has_label=true ;; esac
has_line=false
if grep -qE '^[[:space:]]*No doc impact:[[:space:]]*[^[:space:]]' <<<"$PR_BODY"; then
  has_line=true
fi

if [[ "$has_label" == true && "$has_line" == true ]]; then
  echo "No doc impact declared (label + body line)."
  exit 0
fi

{
  echo "This PR changes code but no docs."
  echo "Update README.md, the matching .claude/rules or .claude/context file, or a documentation/ page,"
  echo "or add a 'No doc impact: <reason>' line to the PR body AND the 'no-docs' label."
  echo "(label present: $has_label, body line present: $has_line)"
} >&2
exit 1
