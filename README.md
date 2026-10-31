# Meal Magician

A private meal planner for one household (or a few). Sign in with Google, create a household,
invite the family, and plan meals day by day: breakfasts, lunches, dinners, and special
occasions like birthdays or Christmas. Reuse meals, single days and whole weeks; rate what you
ate. A later AI assistant will learn the household's preferences and draft plans.

> **Status:** Phase 0 (foundation) in progress. The Gradle skeleton builds, the quality gates
> run, and `task serve` shows a bilingual (hu/en) hello page in the base layout, but there is no
> real feature yet. The plan, decisions and phases are in
> [`PLAN.md`](PLAN.md).

## Stack

| Area | Choice |
|---|---|
| Language / framework | Java 25 (LTS), Spring Boot 4, Spring Modulith (modular monolith) |
| Build | Gradle (Kotlin DSL) + version catalog, Taskfile as the single entry point |
| UI | JTE templates + htmx + Alpine.js + Tailwind (standalone CLI), mobile-first, PWA |
| Auth | Google sign-in (Spring Security OIDC), DB allowlist, email-bound invites |
| Data | PostgreSQL + pgvector, Flyway; H2 for fast local runs/tests |
| AI (later) | Spring AI with a pluggable hosted model, in-process embeddings, pgvector RAG |
| Hosting | Railway (EU), app sleeps when idle; images in GHCR; deploy on release tag |
| CI | GitHub Actions: lint, tests (100 % coverage), migrations on Postgres, e2e (Playwright), Trivy |

## Prerequisites

- Any JDK able to run Gradle. The committed Gradle wrapper and toolchain download Eclipse
  Temurin 25 by themselves (PLAN.md D21).
- [Task](https://taskfile.dev). On Arch the binary is `go-task`, and `task` is often only a
  shell alias.
- Docker + Docker Compose (Testcontainers, local Postgres, e2e)
- `gh` (GitHub CLI), optionally the Railway CLI
- For Claude Code work: the [APM](https://github.com/microsoft/apm) CLI (only to update the shared skills)

Step-by-step setup: [`documentation/Setup.md`](documentation/Setup.md).

## Tasks

`Taskfile.yml` is the single entry point. `task --list` shows everything; the full list, with
which tasks are still to come, is in [`.claude/context/workflow.md`](.claude/context/workflow.md).

```bash
task doctor       # check the required tools
task install      # resolve dependencies, download the Tailwind CLI into .tools/
task format       # format the code
task lint         # Spotless + Checkstyle (Javadoc)
task test         # tests + the 100 % JaCoCo gate
task test:fast    # same without the Docker-based tests (inner loop)
task serve        # run the app on http://localhost:8080 (in-memory H2, no Docker) + Tailwind watch
task up           # start local Postgres + pgvector (Docker); then `task serve:pg`
```

Plain `./gradlew build` still runs the whole quality gate in one go. Run `task install` once
first: every build compiles the Tailwind stylesheet with the CLI it downloads.

## Project structure

Current:

```
meal-magician/
├── CLAUDE.md                 # Claude Code router (rules + context map)
├── PLAN.md                   # working plan: decisions, phases, progress log
├── README.md
├── apm.yml, apm.lock.yaml    # shared Claude Code skills from ai-common (via APM)
├── build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, gradlew   # build (Gradle wrapper pins Gradle 9.8.2)
├── Taskfile.yml, scripts/    # task entry point; doctor.sh (tool check), install-tailwind.sh (pinned CLI download), serve.sh (app + Tailwind watch), generate-icons.sh
├── lombok.config, config/checkstyle/   # Lombok settings; Checkstyle Javadoc rules
├── src/main/java/hu/lasterbergamot/mealmagician/   # app class + one package per module (identity, household, catalog, planning, feedback, sharing, shared); shared/i18n (translations), shared/web (layout plumbing, CSP filter, error + hello pages)
├── src/main/jte/             # JTE templates: layout/, hello/, error/ (fragments are _*.jte)
├── src/main/css/app.css      # Tailwind input; the output static/css/app.css is generated and not committed
├── compose.yml               # local Postgres 17 + pgvector (`task up`)
├── src/main/resources/       # application.yml (+ -dev, -postgres profiles), messages_{hu,en}.properties, static/ (js/ vendored htmx + Alpine CSP, icons/, manifest, sw.js), db/migration/{common,postgresql} (Flyway)
├── src/test/java/…           # ModularityTests, UiGuardTest (bundles + template guards), shared/web slice tests, persistence/ (H2 + Testcontainers Postgres), unit tests
├── .claude/
│   ├── agents/               # convention-reviewer (from ai-common)
│   ├── context/              # workflow, PR checklist, issues, docs map, audit, claude-code
│   ├── rules/                # path-scoped conventions (Java modules, persistence, security, UI, tests, container, docs)
│   ├── skills/               # create-pr, new-issue, … (from ai-common, don't edit here)
│   └── settings.json
├── .github/pull_request_template.md
└── documentation/            # mirrored to the GitHub wiki (Home, Setup, Current-State-*, Roadmap-*, …)
```

The target layout after Phase 0 is in `PLAN.md` §5.

## Contributing

`main` is the only long-lived branch; work on `feat/<issue>-…` branches, commit as
`#N - <summary>`, open a PR (template in `.github/`), merge with a merge commit. Details:
[`.claude/context/workflow.md`](.claude/context/workflow.md) and
[`.claude/context/pr-checklist.md`](.claude/context/pr-checklist.md).
