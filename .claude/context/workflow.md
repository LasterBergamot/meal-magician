# Development workflow, environment variables, branches

Run the tasks from anywhere in the repo. **Status:** the `Taskfile.yml` doesn't exist yet; it
is a Phase 0 item, and this list is its target contents. Keep the list and the Taskfile in sync.

## Tasks (target)

```
task install      # ./gradlew dependencies + download the Tailwind standalone CLI into .tools/
task doctor       # check required tools + versions (scripts/doctor.sh; runbook: documentation/Setup.md)
task lint         # Spotless check + Checkstyle (Javadoc presence)
task format       # Spotless apply (google-java-format)
task serve        # bootRun with the dev profile on H2 + tailwind --watch (no Docker needed)
task serve:pg     # bootRun against the compose Postgres (run `task up` first)
task test         # unit + slice + Testcontainers integration tests, JaCoCo 100 % gate (needs Docker)
task test:fast    # H2-only tests, no Docker (inner loop; CI always runs `task test`)
task test-e2e     # build image, start compose.e2e.yml (app + stub OIDC + Postgres), Playwright tests, tear down
task build        # build the container image (meal-magician)
task up           # start compose.yml (Postgres + pgvector)
task down         # stop it
task migrate-check  # Flyway migrate + validate on a throw-away Postgres (CI runs it)
task docs:check   # docs tooling checks (CI runs it)
```

## Environment variables

Documented in `.env.example` (create it in Phase 0); copy to `.env` (git-ignored, never read by
agents). With no datasource variables, `task serve` uses H2. Google sign-in always needs a
client id/secret; use a separate dev OAuth client (`documentation/Auth-Setup.md`).

| Variable | Used for |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` (local), `prod` (Railway) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres; unset in dev → H2 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OIDC client (`documentation/Auth-Setup.md`) |
| `MM_ADMIN_EMAILS` | comma-separated bootstrap instance admins (always allowlisted) |
| `JAVA_TOOL_OPTIONS` | memory flags on Railway (`-XX:MaxRAMPercentage=75`) |

Under the `prod` profile startup **fails closed** when the Google client, `MM_ADMIN_EMAILS` or a
Postgres URL is missing.

## Branches and releases

- **`main` is the only long-lived branch** (default, protected: PRs only, required checks green,
  no force-push). Feature branches: `feat/<issue>-…`, `fix/<issue>-…`, `chore/<issue>-…`,
  `docs/…`, cut from `main` and PR'd back.
- **Merge commits only** (`gh pr merge <n> --merge`), never squash or rebase. Keep branch history
  clean with `git commit --amend` / `--fixup` + `git rebase --autosquash origin/main`, then
  `git push --force-with-lease` (feature branches only).
- **Commit subjects:** `#N - <imperative summary>`.
- **Releases:** merging to `main` only runs CI. The manual `release.yml` workflow bumps the
  version, tags `vX.Y.Z`, pushes the image to GHCR and deploys it to Railway, then polls
  `/actuator/health` (`documentation/Railway-Setup.md`). Flyway migrates at startup, so
  migrations must stay compatible with the previous release (expand/contract).

## Scheduled workflows

`db-backup.yml` (Phase 6): scheduled `pg_dump` → `age`-encrypted artifact. Nothing periodic runs
inside the app, so Railway's sleeping mode can kick in. GitHub disables scheduled workflows after
60 days without repo activity; check the Actions tab after a long break.
