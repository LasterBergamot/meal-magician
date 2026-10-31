# Audit setup

Read by the `repo-audit` skill (from `ai-common`). The skill holds the procedure; this file
holds what this repo's audit covers, so runs are comparable.

- **Repo and board:** `LasterBergamot/meal-magician`, project TBD (see `issues.md`).
- **Marker line:** `_Found in the <Mon YYYY> repository audit._`

## Code explorer

Read: `src/main/java/**`, `src/main/jte/**`, `src/main/resources/**` (migrations, messages,
application*.yml), `src/test/**`, `src/e2eTest/**`, `build.gradle.kts`, `gradle/libs.versions.toml`,
`lombok.config`. Hunt for:
- queries on household data that are not scoped by household (tenant leaks), 403 vs 404 mistakes;
- module-boundary bypasses (`internal` used across modules) not caught by the Modulith test;
- entity vs Flyway drift (nullability, FKs, indexes, `ON DELETE`), H2-only SQL in common migrations;
- missing `@Version` on edited aggregates; N+1 queries on the week view;
- CSRF/htmx gaps, CSP violations (inline scripts), hard-coded UI strings, missing translations;
- invite/share-token handling (hashing, expiry, revocation, email match);
- tests that assert the wrong thing; coverage exclusions without a reason.

## Docs, infra and CI explorer

Read: `CLAUDE.md`, `.claude/**`, `apm.yml`, `README.md`, `PLAN.md`, `documentation/*`,
`Taskfile.yml`, `compose*.yml`, `Dockerfile`, `.dockerignore`, `.gitignore`, `.github/**`.
Hunt for: docs that contradict the code or each other; PLAN.md items done but unticked;
Docker root user, unpinned images, missing healthcheck; CI permissions, SHA pinning, caching;
anything that would keep Railway from sleeping; ai-common skills edited in place (`apm audit`).

## GitHub state explorer

Collect issues (labels, milestone, parent/sub-issue, blocked-by), board items and fields,
PRs with closing references, open Dependabot PRs and alerts, branch protection.
Cross-reference with PLAN.md §3 and `git log`.
