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
task build        # build the container image (meal-magician) from the Dockerfile
task up           # start compose.yml (Postgres + pgvector) and wait until it is healthy
task down         # stop it
task migrate-check  # runs PostgresMigrationTests: every migration applied to a throw-away pgvector Postgres (needs Docker; CI runs it)
task docs:check   # later (#14): docs tooling checks (CI runs it)
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
- **Releases:** merging to `main` only runs CI. The manual `release.yml` workflow bumps the
  version, tags `vX.Y.Z`, pushes the image to Artifact Registry and deploys it to Cloud Run, then polls
  `/actuator/health` (`documentation/Hosting-Setup.md`). Flyway migrates at startup, so
  migrations must stay compatible with the previous release (expand/contract).

## CI

`.github/workflows/`: `ci.yml` (checks `Lint & test`, `Migrations (Postgres)`, `E2E`),
`docs-check.yml` (`Docs updated`, `scripts/check_docs_touched.sh`) and `wiki-sync.yml`. Reproduce
a failure locally with the `task` target the job calls. Lint workflow edits with
`docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:latest` before pushing. When adding an
action, pin it to a commit SHA with the version in a trailing comment (Dependabot keeps both
current). Dependabot is configured in `.github/dependabot.yml`.

## Scheduled workflows

`db-backup.yml` (Phase 6): scheduled `pg_dump` → `age`-encrypted artifact. Nothing periodic runs
inside the app, so Cloud Run and Neon can scale to zero. GitHub disables scheduled workflows after
60 days without repo activity; check the Actions tab after a long break.
