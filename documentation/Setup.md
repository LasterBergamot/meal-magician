# Setup

The step-by-step way from a fresh clone to a working dev environment, for a person at a terminal
**and** for an AI agent: every step has a command, the result to expect, and what to do when the
result differs. The `setup-project` skill follows only this page, so keep it current.

> **Status:** the application code doesn't exist yet. Steps marked *(Phase 0)* become valid
> once the Gradle project and `Taskfile.yml` land; until then only steps 1–2 apply.

## Ground rules (agents read these first)

- Never install system tools yourself and never use `sudo`; point the person to the README's
  *Prerequisites* and stop.
- Never read `.env`, `.env.*` (except `.env.example`) or `application-local.yml`: they hold credentials.
- Run commands from the repo root.
- At the end, report what is installed, what runs, and what failed or was skipped.

## 1. Check the tools

```bash
java -version && (task --version || go-task --version) && docker compose version && gh --version
```

**Expected:**
- Any JDK that can run Gradle; the wrapper's toolchain downloads Temurin 25 by itself (PLAN.md D21).
- Task 3.x.
- Docker Compose 2.24+.
- gh 2.x.

*(Phase 0: replaced by `task doctor`.)*

**If not:** install the missing tool (README → Prerequisites). On Arch the Task binary is
`go-task`. If `task` is only a shell alias, agents and scripts must call `go-task`.

## 2. Claude Code skills are present

```bash
ls .claude/skills
```

**Expected:** `create-pr new-issue repo-audit setup-project triage-dependabot update-documentation`.

**If not:** the deployed copies are committed; check out a clean tree. Only maintainers updating
them need `GITHUB_APM_PAT=$(gh auth token) apm install`.

## 3. Install dependencies *(Phase 0)*

```bash
task install
```

**Expected:** Gradle resolves dependencies and the Tailwind CLI is in `.tools/`.

## 4. Local secrets *(Phase 0)*

Copy `.env.example` to `.env` and fill in a **dev** Google OAuth client and your email in
`MM_ADMIN_EMAILS` ([[Auth Setup]]). Agents: ask the person to do this; never read `.env`.

## 5. Run the app on H2 *(Phase 0)*

```bash
task serve
```

**Expected:** the app on <http://localhost:8080>; signing in with Google as an admin email shows
the home page; any other email gets 403.

## 6. Run the tests *(Phase 0)*

```bash
task lint && task test
```

**Expected:** both pass; coverage 100 %. `task test` needs Docker (Testcontainers).
