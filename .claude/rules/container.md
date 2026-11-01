---
paths:
  - "Dockerfile"
  - ".dockerignore"
  - "compose*.yml"
  - ".github/**"
  - "Taskfile.yml"
---

# Container, Compose, CI, Cloud Run

- **Dockerfile:** multi-stage (Gradle/JDK 25 build → JRE 25 runtime), base images **pinned by
  digest**, non-root user uid 10001, `HEALTHCHECK` on `/actuator/health`, AppCDS archive built
  at image build time (PLAN.md D17). Only the unpacked boot jar (`app.jar` + `lib/`, from
  `-Djarmode=tools extract`: CDS cannot read a fat jar's nested jars) and the cache go into the
  final image. The cache is a JDK 25 AOT cache (`-XX:AOTCacheOutput`, built on AppCDS) recorded by
  a training run with the `dev` profile (in-memory H2) and `-Dspring.context.exit=onRefresh`. It is
  only valid for the same JRE, classpath and heap flags, so `ENV JAVA_TOOL_OPTIONS` is set before
  the training `RUN`. A stale cache is ignored at run time, never fatal; check the start time
  after touching the Dockerfile or the JVM flags. The runtime image has no curl or wget, so the
  `HEALTHCHECK` uses bash's `/dev/tcp`.
- `JAVA_TOOL_OPTIONS` sets memory via `-XX:MaxRAMPercentage`, never a fixed `-Xmx` larger than
  the Cloud Run memory limit.
- **compose.yml:** Postgres with pgvector for local dev. **compose.e2e.yml:** app image + Postgres
  + a stub OIDC provider (`navikt/mock-oauth2-server`, digest-pinned, config in
  `src/e2eTest/stub-oidc/config.json`), used only by `task test-e2e`. The app discovers the stub as
  `http://oidc:18081` and Chromium maps `oidc` to `127.0.0.1` (`--host-resolver-rules`) with the
  port published under the same number, so token issuer and browser URL agree. The e2e app runs
  with the `postgres` profile, not `prod` (plain HTTP on localhost).
- **CI** (`.github/workflows/`): every job calls `task` targets; `permissions:` minimal per
  workflow; third-party actions pinned to a commit SHA; `concurrency` set; Gradle cache via
  `gradle/actions/setup-gradle`. Required checks report on every PR (no path filters on them).
  Labels and PR titles reach scripts only through `env:` (no `${{ }}` inside `run:`).
- **Release (D13):** `release.yml` is `workflow_dispatch` only (input `version`, main only):
  check version + green CI → bump version on a detached commit → build + push to Artifact
  Registry → `gcloud run deploy` (auth by Workload Identity Federation, no keys; config from
  GitHub variables, secrets via `--set-secrets`, env vars joined with `^|^` because
  `MM_ADMIN_EMAILS` holds emails, so `@` would clash, and may contain commas) → poll `/actuator/health` → push the `vX.Y.Z` tag last.
  Runs queue, never cancel. It is the one workflow with `contents: write` and `id-token: write`.
- **Cloud Run + Neon (D11):** min 0 / max 1 instances, request-based billing; nothing in the app
  or CI may ping the app or the database periodically. Secrets in Secret Manager, config in
  GitHub variables, never in the repo. Runbook: `documentation/Hosting-Setup.md`.
- Trivy scans the image in CI and uploads SARIF.
