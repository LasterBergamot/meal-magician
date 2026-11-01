# Claude Code setup: context layout and skills

## How the project context is organised

| Where | Loaded | Holds |
|-------|--------|-------|
| `CLAUDE.md` | at session start | what the project is, the always-on rules, the router table |
| `.claude/rules/*.md` | when Claude reads or edits a file matching the `paths:` globs | the detail for one area |
| `.claude/context/*.md` | only when you (or a skill) read it | workflow, PR checklist, issue conventions, doc map, audit setup, this file |
| `apm.yml`, `apm.lock.yaml` | by the `apm` CLI | which `ai-common` version the generic skills come from |

- A rule file **without** `paths:` loads at every session start; always give it `paths:`.
- Explore and Plan subagents load neither `CLAUDE.md` nor the rules: name the rule file in their prompt.
- Globs are relative to the repo root. Until the code exists some globs match nothing;
  `DocsToolingTests` (`task docs:check`) fails on dead globs, missing router paths, a rule file
  without `paths:` or not named in `CLAUDE.md`, and `CLAUDE.md` > 100 lines. A glob that matches
  nothing yet goes into its `NOT_YET` list with a reason; the test fails once it starts matching,
  so remove it then.

Which file to update when you change something:

| You changed | Update |
|-------------|--------|
| module structure, events, package rules | `.claude/rules/java-modules.md` |
| entities, Flyway, H2/Postgres notes | `.claude/rules/persistence.md` |
| auth, allowlist, invites, headers, prod config | `.claude/rules/security.md` |
| templates, htmx/Alpine/Tailwind, i18n, PWA | `.claude/rules/web-ui.md` |
| test conventions, fixtures, coverage config | `.claude/rules/tests.md` |
| Dockerfile, Compose, Cloud Run, workflows | `.claude/rules/container.md` |
| docs rules, wiki pages | `.claude/rules/documentation.md` |
| tasks, env vars, branches, release | `.claude/context/workflow.md` |
| PR rules, Dependabot rule, gates | `.claude/context/pr-checklist.md` |
| labels, milestones, epics, board | `.claude/context/issues.md` |
| docs pages added/removed | `.claude/context/docs.md` |
| audit scope | `.claude/context/audit.md` |
| a generic skill or the `convention-reviewer` agent | the `ai-common` repo, not here |
| skills, settings, this layout | this file |

## Skills (`.claude/skills/`)

All current skills come from the owner's private `ai-common` repo via
[APM](https://github.com/microsoft/apm), pinned in `apm.yml` (`workflow#v0.1.1`):
`new-issue` (haiku), `create-pr` (sonnet), `triage-dependabot` (sonnet),
`update-documentation` (sonnet), `repo-audit` (opus), `setup-project` (haiku), and the
`convention-reviewer` agent. They hold the procedure; this repo's values live in
`.claude/context/`.

- The deployed copies are **committed**; `apm_modules/` is git-ignored.
- **Never edit a deployed skill here.** Change it in `ai-common`, tag a release, then here:
  bump the tag in `apm.yml`, `GITHUB_APM_PAT=$(gh auth token) apm install`, review
  `git diff .claude/`, run `apm audit`, commit `apm.yml`, `apm.lock.yaml` and the deployed files together.
- Project-specific skills (none yet) live only here.
- `.claude/settings.json` (shared) allows `task` and `go-task` `lint/test/test:fast/format/build/doctor`, asks before git
  state changes, `gh pr create/merge` and `gcloud`, and denies reading `.env*`.
  `.claude/settings.local.json` is personal and git-ignored.
