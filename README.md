# Meal Magician

A private meal planner for one household (or a few). Sign in with Google, create a household,
invite the family, and plan meals day by day: breakfasts, lunches, dinners, and special
occasions like birthdays or Christmas. Reuse meals, single days and whole weeks; rate what you
ate. An optional AI assistant (off until the owner switches it on) reads the household's own meals
and ratings and drafts plans, which a person always accepts or discards.

> **Status:** Phases 0–5 are built: sign in, households, invites, members, the meal library,
> planning the week, reusing days and weeks, templates and occasions with their menus, rating
> what was eaten (with a meal's "How it went" history), read-only share links and the print view,
> and the PWA install hint, in a storybook design with light and dark themes (#124 to #132).
> The ingredients on meals (Phase 6) and the optional AI assistant (Phase 7: week drafts, a one-slot
> helper, a food chat, occasion menus and the model eval harness, #155 to #162) are built too; the
> assistant stays off until the owner signs off the model (D33, see
> [`documentation/AI-Assistant.md`](documentation/AI-Assistant.md)).
> Still open before v1.0.0: the owner's phone checks, Lighthouse and install (#134). The plan,
> decisions and phases are in [`PLAN.md`](PLAN.md); the redesign of the look and navigation is in
> [`plan-ui-redesign.md`](plan-ui-redesign.md).

## Stack

| Area | Choice |
|---|---|
| Language / framework | Java 25 (LTS), Spring Boot 4, Spring Modulith (modular monolith) |
| Build | Gradle (Kotlin DSL) + version catalog, Taskfile as the single entry point |
| UI | JTE templates + htmx + Alpine.js + Tailwind (standalone CLI), mobile-first, PWA |
| Auth | Google sign-in (Spring Security OIDC), DB allowlist, email-bound invites |
| Data | PostgreSQL + pgvector, Flyway; H2 for fast local runs/tests |
| AI (optional, off by default) | Spring AI with Gemini on Vertex AI (no API key; the model stays behind config), Vertex embeddings, pgvector meal search |
| Hosting | Google Cloud Run + Neon Postgres (Frankfurt), both scale to zero; images in Artifact Registry; automatic release on merge to `main` |
| CI | GitHub Actions: lint, tests (100 % coverage), migrations on Postgres, e2e (Playwright), Trivy |

## Prerequisites

- Any JDK able to run Gradle. The committed Gradle wrapper and toolchain download Eclipse
  Temurin 25 by themselves (PLAN.md D21).
- [Task](https://taskfile.dev). On Arch the binary is `go-task`, and `task` is often only a
  shell alias.
- Docker + Docker Compose (Testcontainers, local Postgres, e2e); Docker Buildx as well for
  `task build` and `task test-e2e` (on Arch the separate `docker-buildx` package)
- `gh` (GitHub CLI, 2.x), required; optionally the Google Cloud CLI (`gcloud`) and the Neon CLI (`neon`, npm package
  `neonctl`; <https://neon.com/docs/reference/neon-cli>), for looking at the deployed app and database
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
task ai:eval -- --models <id,id>   # the assistant eval against REAL Gemini models: costs money, needs Docker + gcloud login, never in CI (see documentation/AI-Assistant.md)
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
├── plan-ui-redesign.md       # UI redesign plan: design system, app shell, components, phases
├── README.md
├── apm.yml, apm.lock.yaml    # shared Claude Code skills from ai-common (via APM)
├── build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, gradlew   # build (Gradle wrapper pins Gradle 9.8.2)
├── Taskfile.yml, scripts/    # task entry point; doctor.sh (tool check), install-tailwind.sh (pinned CLI download), serve.sh (app + Tailwind watch), generate-icons.py (wizard icons from one drawing), check_docs_touched.sh (CI docs check), e2e_needed.sh (CI: skip E2E for docs-only PRs), smoke.sh (post-deploy smoke test, `task smoke -- <url>`), sync-wiki.sh (CI wiki mirror)
├── lombok.config, config/checkstyle/   # Lombok settings; Checkstyle Javadoc rules
├── src/main/java/hu/lasterbergamot/mealmagician/   # app class + one package per module (identity, household, catalog, planning, feedback, sharing, assistant, shared); shared/i18n (translations), shared/web (layout plumbing, CSP filter, error pages)
├── src/main/jte/             # JTE templates: layout/, ui/ (components), dev/ (style guide), login/, error/, household/, admin/, meal/, occasion/, plan/, feedback/ (ratings), sharing/ (print and the public share page) (fragments are _*.jte)
├── src/main/css/app.css      # Tailwind input; the output static/css/app.css is generated and not committed
├── compose.yml               # local Postgres 17 + pgvector (`task up`)
├── .env.example              # variables for local runs (copy to .env, git-ignored)
├── src/main/resources/       # application.yml (+ -dev, -postgres profiles), messages_{hu,en}.properties, static/ (js/ vendored htmx + Alpine CSP, fonts/ self-hosted, icons/, manifest, sw.js), db/migration/{common,postgresql} (Flyway)
├── src/test/resources/eval/  # the assistant eval: cases.json and an invented household.json (code: src/test/java/…/assistant/eval)
├── src/test/java/…           # guard tests (ModularityTests, UiGuardTest, EveryEndpointNeedsSignInTests, DocsToolingTests), tenant/ (isolation), shared/web slice tests, persistence/ (H2 + Testcontainers Postgres), unit tests
├── src/e2eTest/java/…            # Playwright browser suites (sign-in, shell, meal pages, week view, editing and reuse, ratings, share links, themes), run by `task test-e2e`
├── .claude/
│   ├── agents/               # convention-reviewer (from ai-common)
│   ├── context/              # workflow, PR checklist, issues, docs map, audit, claude-code
│   ├── rules/                # path-scoped conventions (Java modules, persistence, security, UI, tests, container, docs)
│   ├── skills/               # create-pr, new-issue, … (from ai-common, don't edit here)
│   └── settings.json
├── .github/                  # workflows/ (ci, docs-check, wiki-sync, release), dependabot.yml, pull_request_template.md
└── documentation/            # mirrored to the GitHub wiki (Home, Setup, Current-State-*, Roadmap-*, …)
```

The target layout after Phase 0 is in `PLAN.md` §5.

## Contributing

`main` is the only long-lived branch; work on `feat/<issue>-…` branches, commit as
`#N - <summary>`, open a PR (template in `.github/`), merge with a merge commit. Details:
[`.claude/context/workflow.md`](.claude/context/workflow.md) and
[`.claude/context/pr-checklist.md`](.claude/context/pr-checklist.md).
