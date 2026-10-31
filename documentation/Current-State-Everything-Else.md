# Current State: Everything Else

What exists in `main` today:

- **Claude Code setup:** router `CLAUDE.md`, `.claude/context/` (workflow, PR checklist, issues,
  docs map, audit, claude-code), path-scoped `.claude/rules/`, `.claude/settings.json`.
- **Shared skills** from `ai-common` `workflow#v0.1.1` via APM (`apm.yml`, `apm.lock.yaml`):
  `create-pr`, `new-issue`, `repo-audit`, `setup-project`, `triage-dependabot`,
  `update-documentation`, and the `convention-reviewer` agent.
- **Docs:** `README.md`, `PLAN.md`, this wiki source in `documentation/`, the PR template.
- **GitHub:** public repo `LasterBergamot/meal-magician`. `main` is protected (PRs required,
  no force-push), merges are merge commits only, the wiki is on. Dependabot alerts and secret
  scanning are on, and the labels, milestones and phase epics (#1–#8) exist.
- **Workflow conventions:** `main`-only branching, merge commits, `#N - subject` commits
  (`.claude/context/workflow.md`).

Not yet: build, CI, container, hosting — see [[Roadmap: Everything Else]].
