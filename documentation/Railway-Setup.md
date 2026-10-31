# Railway Setup

Runbook for hosting on Railway (PLAN.md D11, D13, D17) and the owner's **one setup session**
(D22, D23): every console step is done once, up front, so agents can build, deploy and verify the
rest from CI. **Status:** the setup session is pending; the app is deployed from #17/#18 on.

Order matters: Railway gives the app its domain, and the Google client needs that domain.

1. Railway project (below, part 1)
2. Google OAuth client: [[Auth Setup]] (one client, two redirect URIs)
3. GitHub secret and auto-merge (part 3)
4. Optional: Railway CLI for agents (part 4)

> Railway's console changes from time to time. The labels below are the ones as of late 2026;
> if one differs, look for the closest match. Verify the Hobby-plan price and the sleeping
> ("Serverless") behaviour against the current Railway docs and note what you find here.

## Part 1: the Railway project (about 15 minutes)

1. Open <https://railway.com> and sign up with **GitHub**. Choose the **Hobby** plan (about
   $5/month of usage included; enough for a sleeping app and a small Postgres).
2. **New Project → Deploy a Template**, search **pgvector**, deploy it. Plain Postgres is not
   enough: Flyway's `V2__pgvector.sql` runs `CREATE EXTENSION vector`. Wait until the service is
   green. Note its **exact service name** (for example `pgvector`); you need it in step 6.
   If the project settings offer a region, pick an EU one.
3. In the same project: **+ Create → Empty Service**, rename it `meal-magician`. It stays empty
   until the release workflow deploys the image (#17/#18).
4. In that service: **Settings → Networking → Generate Domain**, port **8080**. Copy the domain
   (like `meal-magician-production.up.railway.app`); it stays as long as the service exists.
5. Same **Settings** page: turn on **Serverless** (app sleeping), so the app sleeps when idle and
   costs almost nothing (D11).
6. Service **Variables → Raw Editor**, paste this (replace `pgvector` with your Postgres service's
   name from step 2), then **Update**. Do not deploy anything:

   ```
   SPRING_PROFILES_ACTIVE=prod
   SPRING_DATASOURCE_URL=jdbc:postgresql://${{pgvector.PGHOST}}:${{pgvector.PGPORT}}/${{pgvector.PGDATABASE}}
   SPRING_DATASOURCE_USERNAME=${{pgvector.PGUSER}}
   SPRING_DATASOURCE_PASSWORD=${{pgvector.PGPASSWORD}}
   MM_ADMIN_EMAILS=<your Google e-mail>
   JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
   GOOGLE_CLIENT_ID=<after part 2>
   GOOGLE_CLIENT_SECRET=<after part 2>
   ```

   `${{service.VAR}}` is Railway's reference syntax: the values come from the Postgres service
   over the private network, so the database password is never copied by hand.
7. **Project Settings → Tokens → Create**: name `github-actions`, environment `production`.
   Copy the token (it is shown once). It lets CI deploy to this project only.

## Part 2: the Google client

Follow [[Auth Setup]] with the domain from part 1, step 4. Then paste the client id and secret into
the two `GOOGLE_*` variables of part 1, step 6, and into your local `.env`.

## Part 3: GitHub (about 3 minutes)

1. Repo on GitHub → **Settings → Secrets and variables → Actions → New repository secret**:
   name `RAILWAY_TOKEN`, value = the token from part 1, step 7.
2. **Settings → General → Pull Requests**: tick **Allow auto-merge**. Agents queue a PR to merge
   once its required checks pass; #16 makes the CI checks required on `main`.

## Part 4 (optional): Railway CLI for agents

1. Install the CLI: <https://docs.railway.com/guides/cli> (on Arch it is in the AUR).
2. In the repo root, in Claude Code: `! railway login` (opens the browser), then
   `! railway link` and pick the `meal-magician` project and the `production` environment.

Agents can then read deploy logs and variable *names* when a deploy fails, instead of asking you
to copy logs.

## When you are done

Tell the agent "setup done" (no values). Nothing else is needed from you until the first deploy,
when you sign in once on the Railway domain (from a phone, if you like) to prove the Google client
works there.

## Health and sleeping

- Health check path: `/actuator/health`.
- After the first deploy, verify: after ~10 idle minutes the service shows as sleeping, and the
  next request wakes it (measure the cold start and note it here).
- If it never sleeps: look for outbound traffic (Hikari idle connections, telemetry).

## Deploys

`release.yml` (manual) builds and pushes the image `ghcr.io/lasterbergamot/meal-magician:<version>`,
then deploys it with `RAILWAY_TOKEN` and polls `/actuator/health`. Record the exact CLI/API command
chosen in #17 here. If the GHCR package turns out private, make it public (the repo is public) or
give Railway registry credentials.

## Backups

Record Railway's native backup options for the plan in use. In addition, `db-backup.yml`
(Phase 6) runs `pg_dump`, encrypts it with `age` and stores it; do a restore drill and document it.
The `age` key is created by the owner in Phase 6.

## Cost

Note the monthly bill after the first full month; compare with the Hobby minimum (D17 review).
