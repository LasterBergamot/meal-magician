# Setup

The step-by-step way from a fresh clone to a working dev environment, for a person at a terminal
**and** for an AI agent: every step has a command, the result to expect, and what to do when the
result differs. The `setup-project` skill follows only this page, so keep it current.

> **Status:** the Gradle skeleton, `Taskfile.yml`, the databases and Google sign-in exist. Once
> steps 1–3 pass, `task lint && task test:fast` (or `./gradlew build`) proves the setup: it
> downloads Gradle, Temurin 25 and the dependencies, then runs the formatter check, Checkstyle,
> tests and the coverage gate. Expect `BUILD SUCCESSFUL`.

## Ground rules (agents read these first)

- Never install system tools yourself and never use `sudo`; point the person to the README's
  *Prerequisites* and stop.
- Never read `.env`, `.env.*` (except `.env.example`) or `application-local.yml`: they hold credentials.
- Run commands from the repo root.
- At the end, report what is installed, what runs, and what failed or was skipped.

## 1. Check the tools

```bash
bash scripts/doctor.sh      # the same check as `task doctor`, for when Task itself is missing
```

**Expected:** `ok` for each required tool and `All required tools are present.`:
- Java 17+: any JDK that can run Gradle; the wrapper's toolchain downloads Temurin 25 by itself (PLAN.md D21).
- Task 3.x.
- Docker with a running daemon, and Docker Compose 2.24+.
- Optional, only reported: `gh` 2.x (needed for issues and PRs), the Google Cloud CLI (`gcloud`), `apm`, and the
  Tailwind CLI (`skip` until step 3).

**If not:** a `FAIL` line names the tool and the problem. Install the missing tool (README →
Prerequisites) and run it again. On Arch the Task binary is `go-task`; if `task` is only a shell
alias, agents and scripts must call `go-task`.

## 2. Claude Code skills are present

```bash
ls .claude/skills
```

**Expected:** `create-pr new-issue repo-audit setup-project triage-dependabot update-documentation`.

**If not:** the deployed copies are committed; check out a clean tree. Only maintainers updating
them need `GITHUB_APM_PAT=$(gh auth token) apm install`.

## 3. Install dependencies

```bash
task install
```

**Expected:** Gradle resolves the dependencies and `.tools/tailwindcss` exists
(`Installed .tools/tailwindcss (v…)`; a second run says it is already there).

**If not:** `Checksum mismatch` means the download was corrupted or tampered with: nothing was
installed, so run it again and report it if it repeats. `No Tailwind standalone CLI for …` means
an unsupported platform (on Windows use WSL).

## 4. Local secrets

Create a **dev** Google OAuth client, copy `.env.example` to `.env` and fill in the client id and
secret and your own e-mail in `MM_ADMIN_EMAILS`. The click-by-click guide is [[Auth Setup]].
Agents: ask the person to do this; never read `.env`.

**Expected:** `.env` exists (it is git-ignored). Without it the app still starts, but signing in
fails at Google with `invalid_client`.

## 5. Run the app

```bash
task serve
```

**Expected:** the app starts on <http://localhost:8080> on in-memory H2 (no Docker needed) and
`curl localhost:8080/actuator/health` answers `{"status":"UP",…}`. The log shows
`The following 1 profile is active: "dev"` and Flyway applying versions 1 and 3. The Tailwind
watcher runs next to the app until you stop it with Ctrl-C. Opening <http://localhost:8080/> shows
the sign-in page (the `EN` link switches the language); signing in with Google as an admin e-mail
gets you in, and any other e-mail gets a 403 (needs step 4).

**If not:** a port clash means something else uses 8080; stop it.

To run against real Postgres with pgvector instead (needs Docker):

```bash
task up && task serve:pg
```

**Expected:** `task up` ends with the container `Healthy`; the app log shows
`PostgreSQL 17` and Flyway applying versions 1 and 2. Stop the database with `task down`.

## 6. Run the tests

```bash
task lint && task test
```

**Expected:** both pass; coverage 100 %. `task test` needs Docker once Testcontainers tests
exist; `task test:fast` skips them and the coverage gate for a quick check without Docker.

## Optional: build the image and run the end-to-end tests

```bash
task test-e2e
```

This builds the container image, starts `compose.e2e.yml` (the app, Postgres and a stub sign-in
provider), signs in with a Chromium browser at phone and desktop size and tears everything down.

**Expected:** the build succeeds. The first run takes a few minutes: it downloads the Gradle and
Tailwind tools into the image build and Playwright's Chromium into `~/.cache/ms-playwright`. Ports
18080 and 18081 must be free. On a distribution Playwright does not support (such as Arch) it
prints a warning and falls back to the Ubuntu build, which works.
<!-- probe -->
