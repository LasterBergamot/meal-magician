# Meal Magician

A private meal planner for one household (or a few). Sign in with Google, create a household,
invite the family, and plan meals day by day: breakfasts, lunches, dinners, and special
occasions like birthdays or Christmas. Reuse meals, single days and whole weeks; rate what you
ate. A later AI assistant will learn the household's preferences and draft plans.

> **Status:** planning done, no application code yet. The plan, decisions and phases are in
> [`PLAN.md`](PLAN.md); development starts with Phase 0 (`PLAN.md` §9).

## Stack (planned)

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

## Prerequisites (planned)

- Any JDK able to run Gradle. The committed Gradle wrapper and toolchain download Eclipse
  Temurin 25 by themselves (PLAN.md D21).
- [Task](https://taskfile.dev). On Arch the binary is `go-task`, and `task` is often only a
  shell alias.
- Docker + Docker Compose (Testcontainers, local Postgres, e2e)
- `gh` (GitHub CLI), optionally the Railway CLI
- For Claude Code work: the [APM](https://github.com/microsoft/apm) CLI (only to update the shared skills)

Step-by-step setup: [`documentation/Setup.md`](documentation/Setup.md).

## Tasks

The target task list is in [`.claude/context/workflow.md`](.claude/context/workflow.md)
(`task serve`, `task test`, `task lint`, `task test-e2e`, …). `Taskfile.yml` arrives in Phase 0.

## Project structure

Current:

```
meal-magician/
├── CLAUDE.md                 # Claude Code router (rules + context map)
├── PLAN.md                   # working plan: decisions, phases, progress log
├── README.md
├── apm.yml, apm.lock.yaml    # shared Claude Code skills from ai-common (via APM)
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
