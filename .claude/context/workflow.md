# Development workflow, environment variables, branches

Run the tasks from anywhere in the repo (`task`, or `go-task` where `task` is only a shell
alias). `Taskfile.yml` is the single entry point (#10); keep this list and the Taskfile in sync.
Tasks marked *later* already exist but stop with a message naming the issue that makes them real.

## Tasks

```
task install      # ./gradlew dependencies + download the Tailwind standalone CLI into .tools/
task doctor       # check required tools + versions (scripts/doctor.sh; runbook: documentation/Setup.md)
task lint         # Spotless check + Checkstyle (Javadoc presence)
task format       # Spotless apply (google-java-format)
task serve        # bootRun with the dev profile on in-memory H2 (no Docker needed) + `tailwindcss --watch` (scripts/serve.sh stops both together)
task serve:pg     # same, with profiles dev,postgres against the compose Postgres (run `task up` first)
task test         # unit + slice + Testcontainers integration tests, JaCoCo 100 % gate (needs Docker)
task test:fast    # skips @Tag("integration") tests (-Pfast): no Docker, no coverage gate (inner loop; CI always runs `task test`)
task test-e2e     # build the image, start compose.e2e.yml (app + stub OIDC + Postgres), run the Playwright tests, tear down (needs Docker; the first run downloads Chromium)
task test-e2e:run # the same without the image build (CI builds the image with a layer cache first)
task build        # build the container image (meal-magician) from the Dockerfile
task smoke -- URL # post-deploy smoke test of a running service, no sign-in (scripts/smoke.sh; release.yml runs it after each deploy; locally: `task smoke -- http://localhost:8080`)
task up           # start compose.yml (Postgres + pgvector) and wait until it is healthy
task down         # stop it
task migrate-check  # runs PostgresMigrationTests: every migration applied to a throw-away pgvector Postgres (needs Docker; CI runs it)
task docs:check   # DocsToolingTests alone: CLAUDE.md ≤ 100 lines, router paths exist, rule globs match (part of `task test`, so CI runs it)
```

Every Gradle build (`task test`, `./gradlew build`, `bootJar`) first runs the Tailwind CLI through
the `tailwindBuild` task, so `task install` is needed once per clone and in CI before the build;
without it the build stops with "run `task install`". `task serve` passes `-PskipTailwind`
because its own `--watch` process builds the stylesheet. The generated `static/css/app.css` is
ignored by Git, not linted and not covered.

The Tailwind CLI version and per-platform SHA-256 hashes are pinned in `scripts/install-tailwind.sh`
(Dependabot can't track them; bump by hand, the script says how).

## Environment variables

Documented in `.env.example`; copy to `.env` (git-ignored, never read by
agents). With no datasource variables, `task serve` uses H2. Google sign-in always needs a
client id/secret; use a separate dev OAuth client (`documentation/Auth-Setup.md`).

| Variable | Used for |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` (local), `prod` (Cloud Run) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres; unset in dev → H2 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OIDC client (`documentation/Auth-Setup.md`) |
| `MM_ADMIN_EMAILS` | comma-separated bootstrap instance admins (always allowlisted) |
| `JAVA_TOOL_OPTIONS` | memory flags on Cloud Run (`-XX:MaxRAMPercentage=75`) |

Under the `prod` profile startup **fails closed** when the Google client, `MM_ADMIN_EMAILS` or a
Postgres URL is missing.

## Branches and releases

- **`main` is the only long-lived branch** (default, protected: PRs only, required checks green,
  no force-push). Feature branches: `feat/<issue>-…`, `fix/<issue>-…`, `chore/<issue>-…`,
  `docs/…`, cut from `main` and PR'd back.
- **Who merges:** for issues in `PLAN.md` the agent merges its own PR once all required checks
  are green (PLAN.md D22); repo settings, Google Cloud, Neon and unplanned work need the owner.
- **Merge commits only** (`gh pr merge <n> --merge`), never squash or rebase. Keep branch history
  clean with `git commit --amend` / `--fixup` + `git rebase --autosquash origin/main`, then
  `git push --force-with-lease` (feature branches only).
- **Commit subjects:** `#N - <imperative summary>`.
- **Parallel subagents (PLAN.md D24):** one subagent per issue, each started with
  `isolation: "worktree"` so it has its own checkout and branch, editing only the files its item
  owns (`plan-ui-redesign.md` §7.1 has the table). The coordinating session files the issues,
  merges the PRs one at a time and keeps the checklists and logs. Branch protection does not
  require an up-to-date branch, so rebase on `origin/main` and wait for green again when `main`
  moved since CI ran. `task lint` and `task test` run side by side in worktrees; `task test-e2e`
  does not (`compose.e2e.yml` binds ports 18080/18081), so leave it to CI or run one at a time.
  Run `task serve` on another port per worktree with `SERVER_PORT=8081 task serve`.
- **Models and effort for subagent work:** see the next section (PLAN.md D25).
- **Releases (#51):** merging code to `main` releases it: `release.yml` starts when CI succeeds on
  `main`, takes the next patch version (`release:minor` / `release:major` on the PR raise the
  other parts) and skips merges that change only Markdown, `documentation/`, `.claude/` or
  `.github/`. A manual run (Actions → Release → Run workflow, or `gh workflow run release.yml -f
  version=0.2.0` / `-f bump=minor`; the version has no `v`) is the fallback, for example to
  redeploy. It runs only on `main`, refuses a version whose tag exists or a commit
  where CI is not green, bumps `version` in `build.gradle.kts` on a detached commit (`main` is
  protected and keeps the SNAPSHOT), pushes the image to Artifact Registry, deploys it to Cloud
  Run and polls `/actuator/health` for about four minutes. Then it runs `task smoke -- <service
  url>` (#58) once: public pages only, no sign-in (see `scripts/smoke.sh`). Only after that does
  it push the tag `vX.Y.Z`, so a tag always means "deployed, healthy and smoke-tested" and a
  failed run can be retried with the same version. The smoke test is not in `ci.yml` (the `E2E`
  job covers the image) and never on a schedule (D11). Needs the GitHub variables listed in `documentation/Hosting-Setup.md`.
  Flyway migrates at startup, so migrations must stay compatible with the previous release
  (expand/contract).

## Models and effort for coordinated work (PLAN.md D25)

Applies to **every** job run as a coordinator plus subagents: UI waves, backend tracks, audits and
anything later. The goal is that the expensive model thinks once, in the plan, and cheaper models
do the typing.

**Names are aliases, never pinned versions.** Say `opus`, `sonnet` or `haiku` (the Agent tool's
`model` values). They always mean the newest release of that family, so no plan, issue or prompt
ever contains an ID such as `claude-sonnet-5-5`. "5.5" in the docs is only what was newest when
they were written. Set the Agent tool's `effort` explicitly (it is allowed here because this file
asks for it).

| Role | Model | Effort | Does |
|------|-------|--------|------|
| Planner | `opus` | high | Writes the wave plan and every issue's `## Execution` brief (below), in an Opus session or a `Plan` subagent with `model: opus` (it loads neither `CLAUDE.md` nor the rules: name the rule files in its prompt). Makes every design decision up front. |
| Coordinator | `haiku` | high | Files the issues from the plan, starts the subagents, merges the PRs one at a time, rebases when `main` moved, ticks `PLAN.md` and adds log rows (D24). Follows the plan literally and never redesigns; a missing decision goes back to the planner or the owner. The owner starts that session on Haiku at high effort. |
| Subagent, default | `haiku` | high | Work that copies an existing pattern: restyling a page from the `ui/` components, docs, translations, CRUD that mirrors another module, tests from a listed table of cases. |
| Subagent, harder | `sonnet` | high | New service or module logic, Flyway migrations, locking and concurrency, tenant- or security-sensitive code, cross-module wiring, anything the brief cannot spell out step by step. |
| Subagent, mechanical | `sonnet` | medium | Edits with a complete recipe: renames, moving paragraphs, version bumps, applying one change across many files. |

The planner picks the model and effort **per issue** and says why in one line. The coordinator
uses exactly that. **Escalation:** if a subagent fails its gates twice, or reports it is blocked
because the work was harder than the brief assumed, the coordinator reruns it one step up (haiku →
sonnet high), not on Opus. If it is blocked on a *decision*, the coordinator stops and asks the
planner or the owner; a cheaper model must not guess.

**The brief is the plan.** A cheaper model works well only when nothing has to be guessed, so each
issue worked by a subagent carries an `## Execution` section (`issues.md`) with:

1. **Model and effort**, and why.
2. **Files owned** (create or edit) by exact path, and the files it must not touch (D24: one writer
   per file per wave).
3. **Read first:** the rule files and the existing file to mirror ("copy the structure of
   `household/_members.jte`"), with line ranges when only a part matters.
4. **Steps in order,** with the exact names: classes, methods, routes, message keys, table and
   column names, the Flyway number.
5. **Tests:** the cases as a table (input → expected) and the guards that must stay green.
6. **Docs:** the files to update and what each paragraph must say.
7. **Commands and what green looks like** (`task lint`, `task test`, the visual check); what is left
   to CI (`task test-e2e` runs one worktree at a time).
8. **Pitfalls** learned in earlier waves that apply here.
9. **Stop conditions:** when to stop and report instead of improvising.
10. The issue's **Acceptance** list is the definition of done.

If the issue is short on any of these, the coordinator sends it back to the planner before starting
a subagent. The wave's table in `PLAN.md` §9 or `plan-ui-redesign.md` §7 lists the model per item
so the owner sees the cost at a glance.

## CI

`.github/workflows/`: `ci.yml` (checks `Lint & test`, `Migrations (Postgres)`, `E2E`),
`docs-check.yml` (`Docs updated`, `scripts/check_docs_touched.sh`) and `wiki-sync.yml`. Reproduce
a failure locally with the `task` target the job calls. Lint workflow edits with
`docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:latest` before pushing. When adding an
action, pin it to a commit SHA with the version in a trailing comment (Dependabot keeps both
current). Dependabot is configured in `.github/dependabot.yml`.

Runners are ephemeral, so `ci.yml` caches what is slow to fetch: the E2E image build uses a
BuildKit `type=gha` layer cache (`docker/build-push-action`, then `task test-e2e:run` and the Trivy
scan reuse that image), and `actions/cache` keeps Chromium (`~/.cache/ms-playwright`) and the
Tailwind CLI (`.tools/`). A new cached step needs a key that changes when its input does.

The `E2E` job always starts, so the required check always reports. A PR that changes only
documentation (Markdown outside `src/`, `documentation/`, `.claude/`) makes `scripts/e2e_needed.sh`
answer `run=false` and the expensive steps carry `if: steps.scope.outputs.run == 'true'`. A new
step in that job needs the same `if`, or it runs on docs-only PRs too. Never skip the job itself
(`if:` on the job, or `paths:` on the workflow): a skipped required check blocks the merge or
passes silently.

## Scheduled workflows

`db-backup.yml` (Phase 6): scheduled `pg_dump` → `age`-encrypted artifact. Nothing periodic runs
inside the app, so Cloud Run and Neon can scale to zero. GitHub disables scheduled workflows after
60 days without repo activity; check the Actions tab after a long break.
