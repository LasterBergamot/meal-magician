# Meal Magician — Claude Code Project Context

This file is a **router**: it holds only what every session needs. The detail for an area lives in
`.claude/rules/` (loads by itself when you read or edit a matching file) and `.claude/context/`
(read it when the table below says so). Read the file for your task before you start.

## What this is

A private, household-only meal planner. A user signs in with Google (DB allowlist), creates or
joins a **household**, and plans its meals per day: breakfast, lunch, dinner and snack. Planned
meals, single days, whole weeks and dated **occasions** (birthdays, Christmas) can be reused or
saved as templates, and members rate what they ate. A later AI assistant (Spring AI + pgvector
RAG, pluggable provider) drafts plans from that history.

**Stack:**
- Java 25 and Spring Boot 4, built with Gradle (Kotlin DSL). A **modular monolith** with Spring Modulith.
- UI: JTE + htmx + Alpine.js + Tailwind (standalone CLI, no Node), mobile-first, PWA.
- Data: Postgres with pgvector and Flyway; H2 for fast tests and `task serve`.
- Hosted on Railway.

All decisions are in `PLAN.md` §2. **Status:** Phase 0 in progress; the Gradle skeleton (#9), Taskfile (#10) and local databases (#11) exist. Next steps are in
`PLAN.md` §9.

## Router: working on X → read Y

| Working on | Read |
|------------|------|
| What to build next, decisions, phases | `PLAN.md` |
| Module boundaries, packages, events | `.claude/rules/java-modules.md` (loads for `src/main/java/**`) |
| Entities, Flyway, H2 vs Postgres, tenant scoping | `.claude/rules/persistence.md` (loads for `src/main/resources/db/**`, `**/internal/*Entity.java`, `**/internal/*Repository.java`) |
| Auth, allowlist, invites, CSP, prod config | `.claude/rules/security.md` (loads for `**/identity/**`, `**/household/**`, `**/config/**`) |
| JTE templates, htmx, Alpine, Tailwind, i18n, PWA | `.claude/rules/web-ui.md` (loads for `src/main/jte/**`, `src/main/resources/static/**`, `src/main/resources/messages*`) |
| Tests and coverage | `.claude/rules/tests.md` (loads for `src/test/**`, `src/e2eTest/**`) |
| Dockerfile, Compose, Railway, CI workflows | `.claude/rules/container.md` |
| Writing docs or wiki pages | `.claude/rules/documentation.md` (loads for `documentation/**`, `README.md`) |
| Setting up a dev machine | `documentation/Setup.md` (the `setup-project` skill follows it) |
| Tasks, environment variables, branches, merge rules | `.claude/context/workflow.md` |
| Opening a PR, Dependabot alerts and PRs | `.claude/context/pr-checklist.md` |
| Filing or editing issues (labels, milestones, epics, board) | `.claude/context/issues.md` |
| Which docs exist and how they are published | `.claude/context/docs.md` |
| Auditing the repo | `.claude/context/audit.md` |
| Skills, settings, APM, how this context is organised | `.claude/context/claude-code.md` |

Explore and Plan subagents load neither this file nor the rules: put the rule file's path in their prompt.

## Always-on rules

- `PLAN.md` is the working plan. Tick its checklist and add a progress-log row (with the real
  issue/PR number) when an item lands; record any change of decision in its D-table as an
  amendment (`*(Amended YYYY-MM-DD; was …)*`).
- **Outward-facing actions need the owner's OK first:** creating or changing the GitHub repo,
  labels, issues or PRs, pushing, merging, Railway or Google Cloud changes.
- On **every** change, check `README.md`, the rule/context file for the area you touched
  (`.claude/context/claude-code.md` has the map) and the matching `documentation/` page(s);
  update whatever the change makes stale. A PR has a docs diff, or a `No doc impact: <reason>`
  line plus the `no-docs` label (CI enforces it once `docs-check.yml` exists).
- **Javadoc is documentation too.** Every new or changed public class, record and method under
  `src/main/java` needs Javadoc that says what it is *for*, the non-obvious *why* and what it
  throws or returns when the signature doesn't show it. Audience: a junior developer new to the
  repo. Checkstyle enforces presence; the content is on you.
- For new code, write tests; for changed code, update tests. **Coverage stays at 100 %**
  (JaCoCo line + branch); every exclusion carries a comment saying why.
- **Tenant safety:** every query on household data is scoped by the current household; another
  household's data returns 404, owner-only actions return 403.
- **No hard-coded UI text:** every user-visible string goes through `messages_hu/en.properties`.
- The generic skills and the `convention-reviewer` agent come from `ai-common` (pinned in
  `apm.yml`). Never edit them here: change the values in `.claude/context/`, or change the
  skill in `ai-common` and bump the tag.
- Before creating a PR, follow `.claude/context/pr-checklist.md`.
- Never edit generated files (`build/`, generated JTE classes, `static/css/app.css` built by
  Tailwind); regenerate them with the `task` targets.
