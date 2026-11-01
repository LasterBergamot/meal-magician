# Doc map

Read by the `update-documentation` skill (from `ai-common`). The skill holds the procedure;
this file says which docs this repo has and the steps that are specific to it.

## Where the docs are

- `documentation/` is the source of the GitHub wiki. `wiki-sync.yml` (Phase 0) mirrors it on
  every push to `main` that touches `documentation/**`. Never edit the wiki directly.
- `README.md`: what the app is, prerequisites, tasks, and the **only** copy of the project
  structure tree.
- `CLAUDE.md` is a router (keep it under 100 lines). Detail lives in `.claude/rules/` and
  `.claude/context/`; `.claude/context/claude-code.md` maps "what you changed" → "which file".
- `PLAN.md`: the working plan (decisions, phases, progress log).
- Other: `.env.example`, Javadoc, generated Spring Modulith docs (`build/spring-modulith-docs`).

## `documentation/` pages

| File | Covers |
|------|--------|
| `Home.md` | Wiki landing page; links to every other page |
| `Setup.md` | Fresh clone → working dev environment, step by step (people and agents) |
| `Newcomer-Guide.md` | Junior-developer walkthrough: tools and why, glossary, one request through the layers, first change |
| `References.md` | Official docs for every library, tool, service and spec |
| `Auth-Setup.md` | Google Cloud OAuth client runbook |
| `Hosting-Setup.md` | owner's one-session setup: Google Cloud (Cloud Run, Artifact Registry, WIF, secrets), Neon, GitHub variables; deploys, scale to zero, backups |
| `Current-State-Backend.md` | Modules, data model, endpoints as they exist today |
| `Current-State-Frontend.md` | Templates, htmx/Alpine patterns, i18n, PWA as they exist today |
| `Current-State-Everything-Else.md` | Container, hosting, CI, dev workflow, conventions |
| `Roadmap-Backend.md`, `Roadmap-Frontend.md`, `Roadmap-Everything-Else.md` | Planned work per area |

Cross-links use `[[Page Title]]` with the page's display title (e.g. `[[Current State: Backend]]`).

## Steps specific to this repo

- Architecture / modules / data model → `Current-State-Backend.md`; UI → `-Frontend.md`;
  container, CI, hosting, workflow → `-Everything-Else.md`. When a roadmap item is done, move it
  from `Roadmap-*` to the matching `Current-State-*` page.
- A new file in `documentation/` must be added to the page map in `wiki-sync.yml` and linked
  from `Home.md`.
- Update the Newcomer Guide when the architecture, request flow, tooling or workflow changes.
- References: add a row for every new library, service, tool or spec.
- Javadoc on every public type/method the change added or altered must still be true.
- No doc change: the PR carries `No doc impact: <reason>` and the `no-docs` label.
