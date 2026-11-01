# Hosting Setup

Runbook for hosting on **Google Cloud Run + Neon Postgres** (PLAN.md D11, D13, D17, D23) and the
owner's **one setup session**: every console step is done once, up front, so agents can build,
deploy and verify the rest from CI. **Status:** the setup session was done by hand on 2026-11-01
(parts 1–8); the app is deployed from #17/#18 on. A script that can check or recreate it is #27.

**What goes where:**

| Thing | Where | Why |
|---|---|---|
| The app | Cloud Run service `meal-magician`, region `europe-west3` (Frankfurt) | scales to zero, pay per request, free tier |
| Container images | Artifact Registry repo `meal-magician` (`europe-west3`) | Cloud Run pulls from it |
| Database | Neon project `meal-magician`, `aws-eu-central-1` (Frankfurt), Postgres 17 | serverless Postgres with pgvector, suspends when idle, free tier |
| Secrets (`google-client-secret`, `db-password`) | Secret Manager | never in the repo or in GitHub |
| Non-secret config (project id, client id, DB URL, admin e-mails) | GitHub Actions *variables* | the deploy workflow passes them to Cloud Run |
| CI's permission to deploy | Workload Identity Federation → service account `mm-deployer` | no long-lived keys anywhere |
| The app's own identity | service account `mm-runtime` | may only read the two secrets |

> Consoles change from time to time; the labels below are the ones as of late 2026. If one
> differs, look for the closest match. Check the current free-tier limits of Cloud Run and Neon
> and note what you find here.

**Before you start:** a credit card for Google Cloud billing (needed even when usage stays free; a
$5 budget alert warns you long before anything real is spent), and about an hour.

## Part 1: Google Cloud project and billing (about 10 minutes)

1. Open <https://console.cloud.google.com/>. If you have no `meal-magician` project yet: **project
   picker → New project**, name `meal-magician`, **Create**, then select it.
2. **☰ → Billing**. If the project has no billing account: **Link a billing account** → create one
   (country, card). New accounts may get free trial credit; that is fine.
3. **Billing → Budgets & alerts → Create budget**: name `meal-magician`, scope this project,
   amount **$5**, keep the alert thresholds (50/90/100 %), e-mail to billing admins. **Finish**.
4. **☰ → Cloud overview → Dashboard**: note the **Project ID** (like `meal-magician-123456`)
   and the **Project number** (digits only). You need both below.

## Part 2: Google sign-in client (about 10 minutes)

Follow [[Auth Setup]] parts "the project and consent screen" and "the client", in this same
project. The two redirect URIs are:

- `http://localhost:8080/login/oauth2/code/google`
- `https://meal-magician-<PROJECT_NUMBER>.europe-west3.run.app/login/oauth2/code/google`

Cloud Run's service URL is predictable (`<service>-<project number>.<region>.run.app`), so the
second URI can be added before the first deploy. Keep the client id and secret at hand.

## Part 3: Cloud Run plumbing, in Cloud Shell (about 10 minutes)

Use **Cloud Shell** (the `>_` icon top right in the console): `gcloud` is installed and signed in
there, nothing to set up on your machine. Paste the blocks one at a time and wait for each to
finish. If Cloud Shell asks to **Authorize**, allow it.

1. Settings used by every later command (replace the two values in `<>`):

   ```bash
   PROJECT_ID=<your project id>
   PROJECT_NUMBER=<your project number>
   REGION=europe-west3
   REPO=LasterBergamot/meal-magician
   gcloud config set project "$PROJECT_ID"
   ```

2. Turn on the APIs (takes a minute):

   ```bash
   gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
     secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
   ```

3. The image repository:

   ```bash
   gcloud artifacts repositories create meal-magician \
     --repository-format=docker --location="$REGION" \
     --description="Meal Magician images"
   ```

4. The two service accounts and their rights:

   ```bash
   gcloud iam service-accounts create mm-runtime --display-name="Meal Magician app"
   gcloud iam service-accounts create mm-deployer --display-name="Meal Magician GitHub deploys"
   RUNTIME=mm-runtime@$PROJECT_ID.iam.gserviceaccount.com
   DEPLOYER=mm-deployer@$PROJECT_ID.iam.gserviceaccount.com

   gcloud projects add-iam-policy-binding "$PROJECT_ID" \
     --member="serviceAccount:$DEPLOYER" --role=roles/run.admin
   gcloud projects add-iam-policy-binding "$PROJECT_ID" \
     --member="serviceAccount:$DEPLOYER" --role=roles/artifactregistry.writer
   gcloud iam service-accounts add-iam-policy-binding "$RUNTIME" \
     --member="serviceAccount:$DEPLOYER" --role=roles/iam.serviceAccountUser
   ```

   `mm-deployer` may deploy Cloud Run services, push images and run the service *as* `mm-runtime`;
   nothing else.

5. Let GitHub Actions of **this repository only** act as `mm-deployer` (Workload Identity
   Federation: GitHub proves who it is with a short-lived token; there is no key to leak):

   ```bash
   gcloud iam workload-identity-pools create github \
     --location=global --display-name="GitHub Actions"
   gcloud iam workload-identity-pools providers create-oidc github-repo \
     --location=global --workload-identity-pool=github \
     --display-name="meal-magician repo" \
     --issuer-uri="https://token.actions.githubusercontent.com" \
     --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
     --attribute-condition="assertion.repository=='$REPO'"
   gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER" \
     --role=roles/iam.workloadIdentityUser \
     --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"
   ```

6. The Google client secret, typed in hidden (it never lands in the shell history or on screen):

   ```bash
   read -rsp 'Google client secret: ' S && echo && \
     printf '%s' "$S" | gcloud secrets create google-client-secret --data-file=- && unset S
   gcloud secrets add-iam-policy-binding google-client-secret \
     --member="serviceAccount:$RUNTIME" --role=roles/secretmanager.secretAccessor
   ```

   Keep Cloud Shell open: part 5 adds the database password the same way.

## Part 4: Neon database (about 5 minutes)

1. Open <https://console.neon.tech/> and sign up (GitHub or Google). The **Free** plan is enough.
2. **Create project**: name `meal-magician`, Postgres **17**, region **AWS Europe Central 1
   (Frankfurt)**, database name `mealmagician`. **Create**.
3. On the project dashboard click **Connect**:
   - Database `mealmagician`, the default role (like `neondb_owner`).
   - Turn **Connection pooling off** (the host must *not* contain `-pooler`; Flyway and Spring
     Session need a direct connection).
   - Note the **host** (like `ep-cool-name-123456.eu-central-1.aws.neon.tech`), the **role** and
     the **password** (shown in the connection string, or **Roles → Reset password**).
4. Nothing else to do in Neon: the app's first start creates the tables and the `vector`
   extension itself (Flyway). Neon suspends the database after a few idle minutes on its own.

## Part 5: the database password into Secret Manager (1 minute)

Back in Cloud Shell (if it timed out, run part 3, step 1 again first):

```bash
RUNTIME=mm-runtime@$PROJECT_ID.iam.gserviceaccount.com
read -rsp 'Neon password: ' S && echo && \
  printf '%s' "$S" | gcloud secrets create db-password --data-file=- && unset S
gcloud secrets add-iam-policy-binding db-password \
  --member="serviceAccount:$RUNTIME" --role=roles/secretmanager.secretAccessor
```

## Part 6: GitHub (about 5 minutes)

1. Repo on GitHub → **Settings → Secrets and variables → Actions → Variables** tab → **New
   repository variable**, once per row (values are config, not secrets; no passwords here):

   | Name | Value |
   |---|---|
   | `GCP_PROJECT_ID` | the project id |
   | `GCP_PROJECT_NUMBER` | the project number |
   | `GCP_REGION` | `europe-west3` |
   | `GOOGLE_CLIENT_ID` | the OAuth client id (ends in `.apps.googleusercontent.com`) |
   | `MM_ADMIN_EMAILS` | your Google e-mail (comma-separate several) |
   | `DB_URL` | `jdbc:postgresql://<neon host>/mealmagician?sslmode=require` |
   | `DB_USERNAME` | the Neon role |

2. **Settings → General → Pull Requests**: tick **Allow auto-merge**.

## Part 7: local sign-in check (about 5 minutes)

1. In the repo root: `cp .env.example .env`, fill in `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
   and `MM_ADMIN_EMAILS` (you, in your editor; agents never read `.env`).
2. `task serve`, open <http://localhost:8080/>, **Sign in with Google** with your admin account →
   the hello page. In a private window, another Google account → a 403 page.
3. Optional, the full check (needs Docker): `task up && task serve:pg`, sign in, restart the app,
   reload → still signed in; then
   `docker compose exec postgres psql -U mealmagician -c "select email from app_user"` lists only you.

## Part 8 (optional): `gcloud` on your machine for agents

So an agent can read Cloud Run logs when a deploy fails, instead of asking you to copy them:
install the Google Cloud CLI (Arch: `google-cloud-cli` from the AUR), then in Claude Code
`! gcloud auth login` and `! gcloud config set project <project id>`. The agent still asks before
any change (CLAUDE.md); it uses this for reading logs and status.

## When you are done

Tell the agent "setup done" plus the result of part 7 (no values). Nothing else is needed from you
until the first deploy (#18), when you sign in once on the `run.app` URL, from a phone if you like.

## Deploys (for #17/#18)

`release.yml` authenticates with `google-github-actions/auth` (provider
`projects/$GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github-repo`,
service account `mm-deployer@$GCP_PROJECT_ID.iam.gserviceaccount.com`), pushes
`europe-west3-docker.pkg.dev/$GCP_PROJECT_ID/meal-magician/app:<version>` and runs
`gcloud run deploy meal-magician` with: `--service-account mm-runtime@…`, `--min-instances 0`,
`--max-instances 1`, `--cpu-boost`, `--port 8080`, `--allow-unauthenticated` (the app does its own
sign-in), env vars `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `_USERNAME`,
`GOOGLE_CLIENT_ID`, `MM_ADMIN_EMAILS`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, and
`--set-secrets GOOGLE_CLIENT_SECRET=google-client-secret:latest,SPRING_DATASOURCE_PASSWORD=db-password:latest`.
It then polls `/actuator/health`. Record the final command here when #17 lands.

## Scale to zero

- Cloud Run: min instances 0, so no instance runs (or costs) between requests; the first request
  after idle pays the cold start. The image's AOT cache makes the app itself start in about
  1.3-1.6 s on a laptop (2.9 s without the cache); the real Cloud Run number is measured at the
  first deploy (#18) and noted here (D17).
- Neon: suspends the compute after ~5 idle minutes; the first query wakes it (well under a second).
- If either never goes idle: look for outbound traffic (Hikari idle connections, telemetry, a
  monitor pinging the app).

## Backups

Neon keeps a point-in-time restore window (check the free-plan length and note it here). In
addition, `db-backup.yml` (Phase 6) runs `pg_dump`, encrypts it with `age` and stores it; do a
restore drill and document it. The `age` key is created by the owner in Phase 6.

## Rotating a secret

```bash
read -rsp 'New value: ' S && echo && \
  printf '%s' "$S" | gcloud secrets versions add google-client-secret --data-file=- && unset S
```

(or `db-password`), then redeploy (re-run the release workflow); delete the old Google secret on
the OAuth client. Update `.env` too for the Google one.

## Cost

Note the monthly bill after the first full month (expected: $0 or cents). The budget alert e-mails
at $2.50 / $4.50 / $5.
