# Railway Setup

Runbook for hosting on Railway (PLAN.md D11, D13, D17). **Status:** not done yet (Phase 0).
Verify plan pricing, sleeping ("Serverless") behaviour, pgvector availability and backups against
the current Railway docs when doing this, and record what you find here.

## Services

1. New project `meal-magician`, region in the EU.
2. **Postgres with pgvector** (a pgvector-enabled template/image). Note its private-network
   variables.
3. **app** service, deployed from the image `ghcr.io/lasterbergamot/meal-magician:<version>`
   (registry credentials if the package is private).

## Variables (app)

`SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (references to the
Postgres service, private network), `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
`MM_ADMIN_EMAILS`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.

## Health and sleeping

- Health check path: `/actuator/health`.
- Enable sleeping on the app service. Then verify: after ~10 idle minutes the service shows as
  sleeping, and the next request wakes it (measure the cold start and note it here).
- If it never sleeps: look for outbound traffic (Hikari idle connections, telemetry).

## Deploys

`release.yml` (manual) builds and pushes the image, then deploys with a Railway project token
(GitHub secret `RAILWAY_TOKEN`) and polls `/actuator/health`. Record the exact CLI/API command
chosen in Phase 0 here.

## Backups

Record Railway's native backup options for the plan in use. In addition, `db-backup.yml`
(Phase 6) runs `pg_dump`, encrypts it with `age` and stores it; do a restore drill and document it.

## Cost

Note the monthly bill after the first full month; compare with the Hobby minimum (D17 review).
