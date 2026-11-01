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
  at image build time (PLAN.md D17). Only the boot jar and the CDS archive go into the final image.
- `JAVA_TOOL_OPTIONS` sets memory via `-XX:MaxRAMPercentage`, never a fixed `-Xmx` larger than
  the Cloud Run memory limit.
- **compose.yml:** Postgres with pgvector for local dev. **compose.e2e.yml:** app image + Postgres
  + a stub OIDC provider, used only by `task test-e2e`.
- **CI** (`.github/workflows/`): every job calls `task` targets; `permissions:` minimal per
  workflow; third-party actions pinned to a commit SHA; `concurrency` set; Gradle cache via
  `gradle/actions/setup-gradle`. Required checks report on every PR (no path filters on them).
  Labels and PR titles reach scripts only through `env:` (no `${{ }}` inside `run:`).
- **Release (D13):** `release.yml` is `workflow_dispatch` only: bump version → tag → build +
  push to Artifact Registry → `gcloud run deploy` (auth by Workload Identity Federation, no
  keys; config from GitHub variables, secrets via `--set-secrets`) → poll `/actuator/health`.
  Runs queue, never cancel.
- **Cloud Run + Neon (D11):** min 0 / max 1 instances, request-based billing; nothing in the app
  or CI may ping the app or the database periodically. Secrets in Secret Manager, config in
  GitHub variables, never in the repo. Runbook: `documentation/Hosting-Setup.md`.
- Trivy scans the image in CI and uploads SARIF.
