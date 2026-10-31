# Before and while creating a PR

Read by the `create-pr` and `triage-dependabot` skills (from `ai-common`). The skills hold
the procedure; this file holds the project's rules and values.

## Repo and branches

- **Repo:** `LasterBergamot/meal-magician`. **Base branch:** `main` (the only long-lived branch).
- **Branch names:** `feat/<issue>-…`, `fix/<issue>-…`, `chore/<issue>-…`, `docs/…`.
- **Protected:** `main` changes only through PRs that pass `Lint & test`, `Migrations (Postgres)`,
  `E2E` and `Docs updated`.
- **Who merges:** for issues in `PLAN.md` the agent merges its own PR once all required checks
  are green (D22); anything else waits for the owner.
- **Merge method:** a merge commit (`gh pr merge <n> --repo LasterBergamot/meal-magician --merge`),
  never squash or rebase. GitHub deletes the remote branch on merge.
- **Commit subjects:** `#N - <imperative summary>`.

## Dependabot

- Before creating a PR, check open alerts (`gh api repos/LasterBergamot/meal-magician/dependabot/alerts`,
  filter `"state": "open"`) and open Dependabot PRs (`gh pr list --state open --author "app/dependabot"`).
- A Dependabot PR is **blocking** if its checks fail, it conflicts, or it is a major bump (or
  touches a core dependency) whose effect can't be shown to be safe; otherwise non-blocking.
  Merge non-blocking ones only after the user confirms, then merge `main` into the branch and
  re-run `task lint` and `task test`.
- **Core dependencies:** Spring Boot (and its BOM), Spring Modulith, Spring Security, Spring AI,
  Flyway, JTE, Hibernate, the Postgres driver, Testcontainers.
- **Lock/catalog:** versions live in `gradle/libs.versions.toml`; never edit generated lock
  files by hand (if dependency locking is enabled: `./gradlew dependencies --write-locks`).

## Gates, in order

1. `task lint` (Spotless + Checkstyle Javadoc presence).
2. `task test`: 100 % JaCoCo line + branch coverage; the Modulith verify, endpoint-auth,
   tenant-isolation and template-literal tests pass. Every new or changed class has tests
   (`.claude/rules/tests.md`).
3. `task test-e2e` if the diff touches `src/main/jte/**`, `src/main/resources/static/**`,
   security config, `Dockerfile` or `compose*.yml`. It needs Docker; tell the user first.
4. `task migrate-check` if the diff touches `src/main/resources/db/**`.

## Docs

- Always run the `update-documentation` skill (doc map: `.claude/context/docs.md`).
- The PR needs a docs diff, or a `No doc impact: <reason>` line in the body plus the `no-docs`
  label (adding a label is state-changing: ask first).
- Use `.github/pull_request_template.md` for the body.

## Issues

- `Closes #N` only when the issue's **Acceptance** checklist is fully met; `Refs #N` otherwise.
- Tick the matching `PLAN.md` checklist item and add a progress-log row in the same PR.
