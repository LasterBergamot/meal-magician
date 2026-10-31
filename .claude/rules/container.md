---
paths:
  - "Dockerfile"
  - ".dockerignore"
  - "compose*.yml"
  - ".github/**"
  - "Taskfile.yml"
  - "railway.json"
  - "railway.toml"
---

# Container, Compose, CI, Railway

- **Dockerfile:** multi-stage (Gradle/JDK 25 build → JRE 25 runtime), base images **pinned by
  digest**, non-root user uid 10001, `HEALTHCHECK` on `/actuator/health`, AppCDS archive built
  at image build time (PLAN.md D17). Only the boot jar and the CDS archive go into the final image.
- `JAVA_TOOL_OPTIONS` sets memory via `-XX:MaxRAMPercentage`, never a fixed `-Xmx` larger than
  the Railway limit.
- **compose.yml:** Postgres with pgvector for local dev. **compose.e2e.yml:** app image + Postgres
  + a stub OIDC provider, used only by `task test-e2e`.
- **CI** (`.github/workflows/`): every job calls `task` targets; `permissions:` minimal per
  workflow; third-party actions pinned to a commit SHA; `concurrency` set; Gradle cache via
  `gradle/actions/setup-gradle`. Required checks report on every PR (no path filters on them).
  Labels and PR titles reach scripts only through `env:` (no `${{ }}` inside `run:`).
- **Release (D13):** `release.yml` is `workflow_dispatch` only: bump version → tag → build +
  push `ghcr.io/lasterbergamot/meal-magician:<version>` → deploy to Railway (`RAILWAY_TOKEN`
  secret) → poll `/actuator/health`. Runs queue, never cancel.
- **Railway (D11):** sleeping enabled on the app service; nothing in the app or CI may ping it
  periodically. Variables in Railway only, never in the repo. Runbook: `documentation/Railway-Setup.md`.
- Trivy scans the image in CI and uploads SARIF.
