# Current State: Everything Else

What exists in `main` today:

- **Claude Code setup:** router `CLAUDE.md`, `.claude/context/` (workflow, PR checklist, issues,
  docs map, audit, claude-code), path-scoped `.claude/rules/`, `.claude/settings.json`.
- **Shared skills** from `ai-common` `workflow#v0.1.1` via APM (`apm.yml`, `apm.lock.yaml`):
  `create-pr`, `new-issue`, `repo-audit`, `setup-project`, `triage-dependabot`,
  `update-documentation`, and the `convention-reviewer` agent.
- **Docs:** `README.md`, `PLAN.md`, this wiki source in `documentation/`, the PR template.
- **GitHub:** public repo `LasterBergamot/meal-magician`. `main` is protected (PRs required,
  no force-push), merges are merge commits only, the wiki is on. Dependabot alerts and secret
  scanning are on, and the labels, milestones and phase epics (#1–#8) exist.
- **Workflow conventions:** `main`-only branching, merge commits, `#N - subject` commits
  (`.claude/context/workflow.md`).

- **Build (#9):** Gradle 9.8.2 (Kotlin DSL, committed wrapper) with versions in
  `gradle/libs.versions.toml`. The Java toolchain (Temurin 25, via the foojay resolver) is
  downloaded automatically, so only *some* JDK is needed to start Gradle. `./gradlew build` runs:
  - **Spotless** (google-java-format for Java, ktlint for `*.gradle.kts`); `./gradlew spotlessApply` fixes formatting.
  - **Checkstyle**, which only checks Javadoc: every public type and method in `src/main/java`
    needs one, and every package needs a `package-info.java`.
  - **Tests** (JUnit 5, AssertJ) and a **JaCoCo gate** requiring 100 % line and branch coverage.
    Excluded, with reasons in `build.gradle.kts`: the class holding `main()` and the generated
    JTE classes. Lombok-generated code is skipped through `lombok.config`.
- **Lombok** is configured (`lombok.config`) but not used yet.
- **Taskfile (#10):** `Taskfile.yml` is the one command surface for people, agents and CI
  (`task --list`). Working today: `install`, `doctor`, `lint`, `format`, `serve`, `test`,
  `test:fast`, `up`, `down`, `serve:pg` and `migrate-check`. `build` starts working when the
  `Dockerfile` lands; `test-e2e` and `docs:check` exist but stop with a message naming the issue
  that implements them. Why it exists: everyone, including CI, runs the same commands,
  so "works on my machine" and "passes in CI" mean the same thing.
  - `scripts/doctor.sh` (`task doctor`) prints `ok`/`FAIL` per tool: Java 17+ (only to start
    Gradle), Task, Docker with a running daemon, Compose 2.24+; `gh`, Railway CLI, `apm` and the
    Tailwind CLI are optional.
  - `scripts/install-tailwind.sh` (`task install`) downloads the Tailwind standalone CLI into
    `.tools/` (git-ignored). The version and each platform's SHA-256 are pinned in the script, and
    a binary that doesn't match is never installed. This keeps the project free of Node.
  - `task test:fast` passes `-Pfast`, which makes Gradle skip tests tagged `@Tag("integration")`
    (the Testcontainers ones), so the inner loop needs no Docker. CI never uses it.

- **Local databases (#11):** two setups, so the inner loop is fast and the truth is real
  (PLAN.md D10).
  - `task serve` runs the `dev` profile on **in-memory H2 in PostgreSQL mode**: no Docker, and
    the data is gone when you stop the app (Flyway rebuilds the schema on every start).
  - `task up` starts `compose.yml`: **Postgres 17 with the pgvector extension**, the image pinned
    by digest, on `localhost:5432` with throw-away dev credentials. `task serve:pg` runs the
    app against it (profiles `dev,postgres`); `task down` stops it, keeping the data volume.
  - The Docker-based tests share one Testcontainers Postgres through
    `AbstractPostgresIntegrationTest`. `task migrate-check` runs `PostgresMigrationTests`, which
    applies every migration to that real Postgres and checks `pgvector` is installed.

Not yet: CI, container, hosting — see [[Roadmap: Everything Else]].
