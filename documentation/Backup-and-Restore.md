# Backup and Restore

Meal Magician's data lives in one Postgres database on Neon. This page explains how that database
is backed up, what you (the owner) set up once, and, most important, how to get the data back on
the day something goes wrong. It is written to be followed under stress by someone who has not
looked at it for months: read "Restore recipes" first if that day is today.

## Why there is a backup at all

Neon's Free plan can rewind the database only about 6 hours. That saves you from a mistake you
notice the same afternoon and from nothing else: not from a bad migration noticed next week, not
from a deleted Neon project, not from a hacked account. So every night a GitHub Actions workflow
(`db-backup.yml`) copies the database to a private Google Cloud Storage bucket. The copy costs
under a cent a month.

Two design choices shape everything below:

- **The backup writer can only add files.** The service account the workflow uses
  (`mm-backup-writer`) may create objects in the bucket and read one secret, nothing more. It
  cannot read, overwrite or delete a backup. If someone stole the workflow's access, they could
  fill the bucket with junk but not destroy or read your data.
- **The bucket is private, and the files in it are plain.** Public access is blocked at the
  bucket level and only you can read it, with your own Google login. The files are not
  encrypted by us; see "Why the backups are not encrypted" below for the reasons and the
  trade-off.

## Why the backups are not encrypted

An earlier design encrypted every dump with a tool called `age`, which would have meant you
keeping a private key forever. It was dropped on purpose:

- **The data is not very sensitive.** It is household e-mail addresses, display names, meal
  plans and rating comments. No payment data, no passwords (sign-in is through Google).
- **The bucket is already locked down.** It is private with public access prevention on, the
  CI account can only create objects (it cannot read any), and Google encrypts everything at
  rest on its own disks.
- **A key is a risk of its own.** A private key you must store and never lose is one more thing
  that can go wrong on the worst day: if the key is lost or does not match, every backup is
  useless and nobody notices until a restore is needed. With plain dumps, restoring needs only
  your Google login.

The trade-off, stated plainly: anyone who can read the bucket can read a dump. That means your
Google account and the owners of the Google Cloud project. And a dump you download to restore
is plain text on your laptop, so **delete it as soon as the restore is done**. If the data ever
becomes more sensitive, encryption can be added back later (a small change to the workflow and
to `scripts/db-restore.sh`).

A backup made before this decision may still be a `.age` file. The restore tool refuses those
with a clear message: decrypt them first with `age` and your old key, then restore the result.
The bucket's clean-up rules delete such files within about six months.

## What is backed up and what is not

**Backed up** (everything in the database, as a `pg_dump --format=custom` file):

- users and the sign-in allowlist, households and members, invites
- the meals catalog, the plan for every day, templates, occasions and their menus
- ratings and read-only share links
- the Flyway history (which database migrations have run) and the table definitions

**Not backed up**, on purpose or because it is not in the database:

- **Sessions** (the `spring_session*` tables): their structure is saved, their rows are not. They
  are throw-away, and restoring them would sign old browsers back in. After a restore everybody
  signs in again with Google; nothing is lost by that.
- **Secrets and settings**: Secret Manager (`db-password`, `google-client-secret`,
  `backup-db-password`), the GitHub variables, the Google OAuth client. Hosting Setup
  ([[Hosting Setup]]) is the recipe to recreate them.
- **The Neon roles and passwords.** A dump holds data, not logins. A restore into a new Neon
  project means a new role and password, which you then give the app.
- **The container images.** They are rebuilt by the release workflow from the code.

The backup also contains personal data (e-mail addresses). That is one reason old backups are
deleted on a schedule and the bucket stays private.

## Where the backups are

Bucket `meal-magician-backups` in Google Cloud, region `europe-west3`, private (public access is
blocked at the bucket level). Object names carry the date and time and end in `.dump`, so no backup ever replaces
another. Three folders, each with its own automatic clean-up (a Google-run lifecycle rule, not
anything the writer does):

| Folder | What goes in | Kept for |
|---|---|---|
| `daily/` | the nightly backup (02:17 UTC) | 7 days |
| `monthly/` | an extra copy made on the 1st of the month | about 190 days (six months) |
| `release/` | a copy taken right before each deploy, so a bad migration can be undone | 14 days |

So the oldest thing you can get back is about six months old, and the newest is at most a day old
(plus whatever happened since the last run). Anything older is gone; that limit is deliberate, to
keep the bill at cents and old personal data from piling up.

List what exists (you need to be signed in to `gcloud` with your own Google account, the project owner; the first time, run `gcloud auth login`):

```bash
gcloud storage ls gs://meal-magician-backups/daily/
gcloud storage ls gs://meal-magician-backups/monthly/
gcloud storage ls gs://meal-magician-backups/release/
```

## One-time setup (the owner's steps)

Do this once, after Hosting Setup parts 1 to 6. Agents write the code and run the commands you
approve; the passwords are yours alone. Your steps are: create the Neon read-only role and its
secret, run the setup script, set the `BACKUP_BUCKET` variable, check that the writer is
create-only, and run the first backup.

**Do them in this order.** The nightly workflow (`db-backup.yml`) is also called by `release.yml`
before every deploy, so a release waits for a backup. Merge the workflow into `main` only after
steps 1 to 5, otherwise every release stops at a backup job that cannot succeed yet. Do the steps in
your own terminal: the script asks `y/N` before every Google Cloud change, and the database password
must never reach a chat or a file.

`<project id>`, `<project number>` and `<app role>` below are yours: the first two are the
`GCP_PROJECT_ID` and `GCP_PROJECT_NUMBER` GitHub variables, and `<app role>` is the database role
the app connects as (the `DB_USERNAME` variable).

### 1. Give the backup a read-only database role

The dump logs in to Neon as a role that can only read, so even a leaked backup password cannot
change or delete data.

1. Generate a password and copy it: `openssl rand -base64 24`. You will paste it twice (here and in
   step 2). Do not save it in a file; if you lose it, create the role again with a new one.
2. In the Neon console open the project, its **SQL Editor**, database `mealmagician`, and run:

   ```sql
   CREATE ROLE mm_backup LOGIN PASSWORD '<the password>';
   GRANT CONNECT ON DATABASE mealmagician TO mm_backup;
   GRANT USAGE ON SCHEMA public TO mm_backup;
   GRANT SELECT ON ALL TABLES IN SCHEMA public TO mm_backup;
   GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO mm_backup;
   ALTER DEFAULT PRIVILEGES FOR ROLE "<app role>" IN SCHEMA public GRANT SELECT ON TABLES TO mm_backup;
   ALTER DEFAULT PRIVILEGES FOR ROLE "<app role>" IN SCHEMA public GRANT SELECT ON SEQUENCES TO mm_backup;
   ```

   The last two lines are why this role keeps working: tables that Flyway adds in later migrations
   become readable too, without anyone coming back here.
3. **If an `ALTER DEFAULT PRIVILEGES` line says "permission denied",** the editor is connected as a
   different role than the one that owns the tables. Connect as `<app role>` with `psql` (use that
   role's connection string from the Neon dashboard) and run just those two lines there.
4. Optional check: `psql -h <host from DB_URL> -U mm_backup -d mealmagician` and type the password at
   the prompt. `select count(*) from app_user;` must work, and `create table x(a int);` must be
   refused.

### 2. Store the password in Secret Manager

Run this in your own terminal. It asks for the password hidden, so paste the one from step 1:

```bash
read -rsp 'mm_backup password: ' S && echo && \
  printf '%s' "$S" | gcloud secrets create backup-db-password --project=<project id> --data-file=- && unset S
```

The setup script also pauses for exactly this (and the SQL above) if the secret does not exist yet,
so doing it first only saves a pause.

### 3. Set the GitHub variable

Repo → Settings → Secrets and variables → Actions → Variables ([[Hosting Setup]] part 6), or:

```bash
gh variable set BACKUP_BUCKET --body meal-magician-backups --repo LasterBergamot/meal-magician
```

- `BACKUP_BUCKET`: `meal-magician-backups`.

The workflow also reads `DB_URL`, which must be Neon's **direct** endpoint (no `-pooler` in the
host name): the dump takes an exported snapshot, and a pooled connection cannot share one.

### 4. Run the Google Cloud setup script

`scripts/backup-gcp-setup.sh` creates the bucket and its clean-up rules, the `mm-backup-writer`
account with exactly the rights above, the link that lets this repository's workflows (and no
other) use that account, and an alert e-mail. Every change is a "step": it shows the command,
asks `Run this? [y/N]`, and runs it only on `y`. Anything already created is skipped, so running
it twice is safe. Run it in your terminal from the repository root (or in Cloud Shell):

```bash
PROJECT_ID=<project id> PROJECT_NUMBER=<project number> ALERT_EMAIL=<your e-mail> \
  bash scripts/backup-gcp-setup.sh
```

`ALERT_EMAIL` is where the "no backup for two days" alert goes. In order, the script:

1. turns on the Cloud Storage and Cloud Monitoring APIs;
2. creates the private bucket `gs://meal-magician-backups` in `europe-west3` (uniform access,
   public access prevention) and applies the clean-up rules (`daily/` 7 days, `monthly/` 190 days,
   `release/` 14 days);
3. creates the `mm-backup-writer` service account and lets it create objects in that bucket, and
   nothing else;
4. checks that the secret from step 2 exists (if not, it prints the SQL and the command and waits),
   then lets the writer read that one secret;
5. lets this repository's GitHub Actions act as the writer (Workload Identity Federation);
6. creates the e-mail channel and the alert policy.

Read each command before you answer `y`; Google Cloud changes are yours to approve. If you skip a
step, the script ends with exit code 3 and lists what is left: run it again to do those.

### 5. Check that the writer really is create-only

The script ends by printing four commands. Run them once: the first uploads a test file as the
writer and must succeed; reading, overwriting and deleting that file as the writer must each be
refused with HTTP 403. Then delete the test file as yourself. This is the check that the
"cannot destroy backups" promise is true, not just intended.

- **Token Creator.** `--impersonate-service-account` needs the role Service Account Token Creator
  on the writer. If the first command says you cannot impersonate it, give yourself the role for
  the check and take it away afterwards:

  ```bash
  W=mm-backup-writer@<project id>.iam.gserviceaccount.com
  gcloud iam service-accounts add-iam-policy-binding $W --project=<project id> \
    --member=user:<your e-mail> --role=roles/iam.serviceAccountTokenCreator
  # ... run the four commands, delete the test file as yourself, then:
  gcloud iam service-accounts remove-iam-policy-binding $W --project=<project id> \
    --member=user:<your e-mail> --role=roles/iam.serviceAccountTokenCreator
  ```

- **If the first command (the upload) fails with 403 naming `storage.objects.get`,** stop. That is
  the known risk of a create-only role: `gcloud storage cp` may look at the destination first. The
  fix is one step in `db-backup.yml`: upload with a `curl` call to the JSON API using
  `ifGenerationMatch=0` (create only if the object does not exist) instead of `gcloud storage cp`.
  Nothing else changes. Ask an agent to make that change before the first backup.

### 6. Run the first backup and look at it

Merge the workflow pull request. The merge starts a release, and the release now begins with a
backup (`kind: release`), so it is already the first live test; look at its "Back up the database"
job in the Actions tab. Then run the nightly path by hand: Actions tab → `db-backup.yml` → Run
workflow. When it is green, `gcloud storage ls gs://meal-magician-backups/daily/` and
`.../release/` each show a `.dump` file.

Then:

- **Check the alert's filter once.** In Monitoring → Metrics Explorer choose the metric
  `storage.googleapis.com/api/request_count`, resource Cloud Storage Bucket, group by `method`. The
  upload must show up as `InsertObject` or `WriteObject`; the alert counts exactly those. If it is
  named differently, change the filter in the alert policy.
- **Check the budget.** In Billing → Budgets the budget must cover the whole project, not one
  service, so the bucket is counted (expect cents a month).
- Do the optional 15-minute restore check below once.

## Restore recipes

All three start the same way: pick a backup, then pick where to restore it.

Sign in to Google Cloud with your own account once (`gcloud auth login`); that login is the only
"key" a restore needs.

The tool is `task db:restore` (it runs `scripts/db-restore.sh`). It does two things: fetches the
backup (a `gs://` name is copied with `gcloud`; a file on disk is used as is) and runs
`pg_restore` into the target. A copy it fetched itself exists only in a private temporary folder
that is deleted when the tool ends, even if it fails. A file you downloaded yourself stays where
it is, and it is the whole database in plain text: **delete it after the restore**. For the Neon
targets you need `pg_restore` of PostgreSQL 17 on your machine.

`pg_restore` is run with `--clean --if-exists`: it **drops** each table in the backup before
recreating it. That is right for an empty or throw-away target and exactly why the tool asks
before touching a database that already has data. Exit codes: 0 restored, 1 a step failed, 2
nothing was done (wrong arguments, or you did not confirm).

### Recipe A: into the local Postgres (to look, or to test the backup)

Use this to check a backup, or to work on a copy of production data. It never touches Neon.

```bash
docker compose down --volumes     # throws away the local database
task up                           # a fresh, empty Postgres (waits until healthy)
task db:restore -- gs://meal-magician-backups/daily/<object-name> --target local
task serve:pg                     # starts the app on the restored data
```

Sign in with Google on <http://localhost:8080/> with an account on the allowlist and look at a
few plans. If the local database already holds tables, the tool refuses and asks for `--yes`.

### Recipe B: into a new Neon branch (production data damaged, database still there)

Use this when the Neon project is fine but its data is wrong (a bad migration, a bad deletion).
A branch is a cheap copy inside the same project, so nothing is lost if you pick the wrong
backup: the broken data stays on the original until you switch.

1. In the Neon console: your project → Branches → **Create branch** (any name, for example
   `restore-<date>`). Open **Connect** for that branch: database `mealmagician`, the app's role,
   **Connection pooling off** (the host must not contain `-pooler`), and copy the connection string.
2. Give the URL to the tool through the environment, typed hidden so it stays out of your shell
   history, and restore:

   ```bash
   read -rsp 'Neon branch URL: ' RESTORE_DB_URL && echo && export RESTORE_DB_URL
   task db:restore -- gs://meal-magician-backups/release/<object-name> --target neon-branch
   ```

   The tool shows the host and database and waits for you to type `yes`. Read the host: it must
   be the new branch, **not** the live endpoint.
3. Point the app at the branch (the tool prints this list but changes nothing itself): set the
   GitHub variables `DB_URL` (new host) and `DB_USERNAME` if they differ, put the branch's
   password into the `db-password` secret ([[Hosting Setup]], "Rotating a secret") and redeploy
   by running the release workflow.
4. Sign in and check the data. Once happy, make the branch the project's main one in Neon (or
   keep using it) and keep the old branch for a few days before deleting it.
5. `unset RESTORE_DB_URL`, and delete any dump file you downloaded.

### Recipe C: into a new Neon project (the project itself is gone or unusable)

1. Recreate the project as in [[Hosting Setup]] part 4 (Postgres 17, Frankfurt, database
   `mealmagician`, connection pooling off). Do **not** deploy yet.
2. Restore exactly like recipe B, step 2, with `--target neon-project` and the new project's URL.
3. Everything that named the old database now needs the new one: `DB_URL`, `DB_USERNAME`, the
   `db-password` secret, then redeploy. The read-only role `mm_backup` does not exist in the new
   project (roles are not in a dump): create it again with the SQL from step 1 of "One-time setup" above,
   store its new password in `backup-db-password` (same `gcloud secrets versions add`
   pattern as "Rotating a secret"), and point the backup job at the new host before the next
   nightly run.
4. Sign in and check the data.

## How you know a backup is good

**What the nightly job proves.** Every night the workflow does not just copy the database. It
restores the fresh dump into a throw-away Postgres, checks that the Flyway history reaches the
latest migration and that row counts match the source (sessions excluded). A dump that cannot be
restored fails the job, which opens, or comments on, one issue titled `Backup failed` and e-mails
you through GitHub. So a green night means: the dump is complete and `pg_restore` can read it.

**What it cannot prove**, and what covers each gap:

| Gap | Why the job cannot see it | What covers it |
|---|---|---|
| The app starts and works on the restored data | The job checks tables and counts, not the application | The optional check below |
| The job keeps running | A job that never starts leaves no failed run | The "no backup for two days" alert |

You declined a recurring manual drill, so none is required. If you want one anyway, this takes
about 15 minutes and is worth doing once after setup and again after any big change to the
database:

1. Recipe A with the newest `daily/` backup.
2. `task serve:pg`, sign in, open this week's plan and a template.
3. If it works, you are done; if not, treat it as a failed backup and
   open an issue.

## If the alert fires

Cloud Monitoring e-mails you ("Meal Magician: no database backup for 2 days") when no upload has
reached the bucket for about two days. This lives in Google Cloud on purpose: if GitHub stopped
the schedule, a check inside GitHub would stop with it. Likely causes, in order:

1. **GitHub switched the schedule off.** In a public repository GitHub disables scheduled
   workflows after 60 days without repository activity. Open the Actions tab; a banner on
   `db-backup.yml` offers to re-enable it. Use **Run workflow** to take a backup now.
2. **The job is failing.** Look for the open `Backup failed` issue and the latest run's log
   (a changed Neon password for `mm_backup`, a Neon outage, a failed test restore).
3. **The upload is refused.** Check the run log for HTTP 403 from Cloud Storage (the workload
   identity link or the bucket rights were changed).
4. **The alert is wrong, not the backups.** `gcloud storage ls gs://meal-magician-backups/daily/`
   shows a file from the last day or two. Then the alert's metric filter no longer matches
   (see below).

After a fix, the next successful upload closes the alert by itself.

How the alert works, in case you need to change it: it watches the metric
`storage.googleapis.com/api/request_count` of the bucket (Cloud Storage's own request counter; no
extra logging to pay for) for upload requests (`InsertObject`, `WriteObject`). It is a threshold
alert, "fewer than one upload in the last 23 hours", that has to stay true for another 24 hours,
and missing data counts as zero. With a nightly backup the 23-hour count never stays at zero for
24 hours; after a missed night it does, so the e-mail comes roughly 47 hours after the last
upload. A plain "no data" alert cannot be used because Cloud Monitoring limits it to one day,
which would fire every night. The setup script has the policy as JSON. Right after setup, and
before the first backup, the alert fires about a day later if nothing has been uploaded yet; that
is correct. If the first real upload does not show under that method name in Metrics Explorer,
fix the filter in the policy (the script prints how).
