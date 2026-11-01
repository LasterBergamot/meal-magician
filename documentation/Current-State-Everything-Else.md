# Current State: Everything Else

What exists in `main` today:

- **Claude Code setup:** router `CLAUDE.md`, `.claude/context/` (workflow, PR checklist, issues,
  docs map, audit, claude-code), path-scoped `.claude/rules/`, `.claude/settings.json`.
- **Shared skills** from `ai-common` `workflow#v0.1.1` via APM (`apm.yml`, `apm.lock.yaml`):
  `create-pr`, `new-issue`, `repo-audit`, `setup-project`, `triage-dependabot`,
  `update-documentation`, and the `convention-reviewer` agent.
- **Docs:** `README.md`, `PLAN.md`, this wiki source in `documentation/`, the PR template.
- **GitHub:** public repo `LasterBergamot/meal-magician`. `main` is protected (PRs required,
  no force-push, and the checks `Lint & test`, `Migrations (Postgres)`, `E2E` and `Docs updated`
  must pass; the owner can still bypass as an admin), merges are merge commits only, the wiki is on. Dependabot alerts and secret
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
  `test:fast`, `up`, `down`, `serve:pg`, `migrate-check`, `build` and `test-e2e` (#15).
  `docs:check` (#14) runs `DocsToolingTests` alone; the full `task test` runs it too. Why the tasks exist: everyone, including CI, runs the same commands,
  so "works on my machine" and "passes in CI" mean the same thing.
  - `scripts/doctor.sh` (`task doctor`) prints `ok`/`FAIL` per tool: Java 17+ (only to start
    Gradle), Task, Docker with a running daemon, Compose 2.24+, `gh` 2.x; `gcloud`, `neon`, `apm` and the
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

- **Tailwind in the build, and `task serve` (#13):** the Gradle task `tailwindBuild` runs the
  pinned CLI (`.tools/tailwindcss`) to produce the minified `static/css/app.css` and runs before
  `processResources`, so every `./gradlew` build, test run and `bootJar` includes a fresh
  stylesheet. Without `task install` it fails with a message saying so (CI therefore runs
  `task install` first). The output is generated, ignored by Git, and neither Spotless nor
  Checkstyle looks at it. Checkstyle also skips the JTE-generated Java classes
  (`gg/jte/generated`); the templates are the source.
  `task serve` and `task serve:pg` call `scripts/serve.sh`, which starts `tailwindcss
  --watch=always` in the background and `./gradlew bootRun -PskipTailwind` in the foreground, and
  stops the watcher when the app stops (Ctrl-C). The `dev` profile serves static files straight
  from `src/main/resources/static`, so CSS and JS edits show after a browser reload.
- **Icons:** `scripts/generate-icons.sh` regenerates the placeholder PWA icons with ImageMagick;
  the PNGs are committed, so nobody needs ImageMagick for a normal build.

- **Container image and e2e tests (#15):**
  - `Dockerfile` is multi-stage and both base images are pinned by digest. The build stage
    (Temurin 25 JDK) downloads the checksum-pinned Tailwind CLI, runs `./gradlew bootJar` and
    unpacks the jar; the runtime stage (Temurin 25 JRE) copies it in, runs the app once to record a
    **JDK 25 AOT cache** (`app.aot`, the successor of an AppCDS archive) and starts as uid 10001
    (`app`) with a `HEALTHCHECK` on `/actuator/health`. Why a training run: the JVM then loads
    the hundreds of Spring classes from the cache instead of parsing them at every start, which
    matters because Cloud Run starts a fresh instance after every idle period (PLAN.md D11, D17).
  - **Cold start**, measured on the dev laptop (the app only; Cloud Run's CPU is slower, so
    #18 measures the real number): about 2.9 s without the cache and 1.3 s with it on in-memory H2;
    1.6 s on Postgres including three Flyway migrations.
  - `compose.e2e.yml` starts the image, a pgvector Postgres and a stub OIDC provider
    (`navikt/mock-oauth2-server`) that signs in `e2e@example.test` without a login form. The
    app's `google` registration is pointed at it through `SPRING_SECURITY_OAUTH2_CLIENT_*`
    variables, so the real `oauth2Login` code path, the allowlist and the session store run.
  - `src/e2eTest` (Playwright for Java, Gradle task `e2eTest`) opens `/`, follows the sign-in and
    checks the hello page at 390×844 and 1280×800, including that the page does not scroll
    sideways. `task test-e2e` builds the image, runs the stack, the tests and tears the stack down
    even if they fail. Not covered: the `prod` profile and HTTPS (the stack runs plain HTTP on
    localhost) and real Google.

- **CI (#16):** three workflows in `.github/workflows/`. Every job calls the same `task` targets
  a developer runs, so a green PR means "passes on a clean machine".
  - `ci.yml` has the checks **Lint & test** (`task lint`, `task test`, the 100 % gate),
    **Migrations (Postgres)** (`task migrate-check`) and **E2E** (builds the image, `task test-e2e:run`,
    then a Trivy scan of that same image: the table step fails the check on a fixable
    HIGH/CRITICAL finding, the SARIF upload runs on `main` only). Actions are pinned to a commit
    SHA, permissions are read-only, and a newer push cancels the older run.
  - **Caches (#37)** keep the E2E job short, because GitHub runners start empty. The image is built
    by `docker/build-push-action` with a BuildKit layer cache in the GitHub Actions cache
    (`type=gha`, `mode=max`) and loaded as `meal-magician`; the job then runs `task test-e2e:run`
    (the `test-e2e` steps without the build) and Trivy scans that image instead of building it a
    third time. The Playwright Chromium download (`~/.cache/ms-playwright`, keyed on
    `gradle/libs.versions.toml`) and the Tailwind CLI (`.tools/`, keyed on
    `scripts/install-tailwind.sh`, all three jobs) are cached with `actions/cache`. The Gradle
    cache mount inside the Dockerfile is not exported by the layer cache, so a changed source file
    still re-downloads the dependencies in the image build.
  - **Docs-only PRs skip the E2E work (#37).** The **E2E** job still starts and reports, so the
    required check is never pending or skipped (branch protection is unchanged). Its first step,
    `scripts/e2e_needed.sh`, compares the PR with its base; when every changed file is Markdown
    outside `src/`, or under `documentation/` or `.claude/`, the image build, Playwright and Trivy
    steps are skipped and the job passes in about 15 seconds. Anything else, an empty diff or a
    push to `main` runs everything, and a failure of the script itself fails the job. Edits to
    `ci.yml`, the Dockerfile, the compose files or the script count as code. The trade-off: a
    docs-only PR gets no fresh Trivy scan; `main` still gets one on every push.
  - `docs-check.yml` is the **Docs updated** check (`scripts/check_docs_touched.sh`). A PR must
    touch `README.md`, `CLAUDE.md` or a file under `documentation/`, `.claude/rules/` or
    `.claude/context/`; or change only Markdown; or carry the `no-docs` label *and* a
    `No doc impact: <reason>` line in its body. `PLAN.md` next to code does not count, because
    every PR edits it. Dependabot PRs are exempt.
  - `wiki-sync.yml` copies `documentation/` into the GitHub wiki on every merge to `main` that
    touches it (`scripts/sync-wiki.sh`). The page map in the workflow turns `Current-State-Backend.md`
    into the wiki page "Current State: Backend", which is what `[[wiki links]]` expect. The
    wiki is overwritten each time, so never edit it by hand. The wiki's git repo exists only
    after the first page is created in the web UI.
  - `.github/dependabot.yml` opens weekly update PRs against `main` for Gradle, GitHub Actions
    and Docker/Compose, with minor and patch bumps of Gradle and Actions grouped in one PR each.
  - `release.yml` (#18) is the manual release: version in, image in Artifact Registry, new
    Cloud Run revision, health check, tag. It is the only workflow that can deploy or push a tag,
    and it starts only when someone runs it. Details and the variables it needs:
    [[Hosting Setup]]. **v0.1.0** was released with it on 2026-11-01 and the service
  answers on its `run.app` URL (health 200, `/` redirects to the login page, Flyway applied the migrations on Neon).

Not yet confirmed: sign-in on a phone and scale to zero — see [[Roadmap: Everything Else]].
