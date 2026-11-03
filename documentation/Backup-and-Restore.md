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
- **Backups are encrypted so that only you can read them.** The workflow encrypts each dump with
  your *public* `age` key, which is safe to keep in a GitHub variable. The matching *private* key
  lives only in your password manager. CI can write backups; nothing in CI can open one.

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

The backup also contains personal data (e-mail addresses). That is the other reason it is
encrypted, and why old backups are deleted on a schedule.

## Where the backups are

Bucket `meal-magician-backups` in Google Cloud, region `europe-west3`, private (public access is
blocked at the bucket level). Object names carry the date and time, so no backup ever replaces
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

List what exists (you need to be signed in to `gcloud` as the project owner):

```bash
gcloud storage ls gs://meal-magician-backups/daily/
gcloud storage ls gs://meal-magician-backups/monthly/
gcloud storage ls gs://meal-magician-backups/release/
```

## One-time setup (the owner's steps)

Do this once, after Hosting Setup parts 1 to 6. Agents write the code and run the commands you
approve; the key and the passwords are yours alone.

### 1. Make the `age` key and check it

On your own machine (install `age`: Arch `pacman -S age`, macOS `brew install age`):

```bash
age-keygen -o mm-backup-key.txt
```

It prints `Public key: age1...`. The file `mm-backup-key.txt` holds the private key.

1. Put the **whole file** into your password manager (a secure note). That is the only copy:
   there is no paper copy and no second key, by choice. Losing it makes the backups unreadable
   but never touches the live database.
2. **Check the public key.** Run

   ```bash
   age-keygen -y mm-backup-key.txt
   ```

   and compare what it prints, character by character, with the public key you are about to
   store as the GitHub variable. They must be identical. Why this matters: the nightly job
   proves the dump can be restored, but it cannot prove that *your* private key matches the
   public key in the variable. If you pasted the wrong key into the variable, every backup would
   look healthy and none could ever be opened by you. This one command is the only way to find
   out now instead of on the worst day.
3. Delete the file from the disk (`shred -u mm-backup-key.txt`, or `rm` on a machine without
   `shred`) once it is in the password manager and you have opened the stored copy once to see
   it is complete.

### 2. Set the GitHub variables

Repo → Settings → Secrets and variables → Actions → Variables ([[Hosting Setup]] part 6):

- `BACKUP_AGE_RECIPIENT`: the public key (`age1...`) from step 1.
- `BACKUP_BUCKET`: `meal-magician-backups`.

### 3. Give the backup a read-only database role

The dump logs in to Neon as a role that can only read, so even a leaked backup password cannot
change or delete data. The setup script (next step) prints the exact SQL to run in the Neon SQL
editor and the command that stores the new password in Secret Manager as `backup-db-password`.
You type the password yourself, hidden; it is never shown to an agent or written to a file.

### 4. Run the Google Cloud setup script

`scripts/backup-gcp-setup.sh` creates the bucket and its clean-up rules, the `mm-backup-writer`
account with exactly the rights above, the link that lets this repository's workflows (and no
other) use that account, and an alert e-mail. Every change is a "step": it shows the command,
asks `Run this? [y/N]`, and runs it only on `y`. Anything already created is skipped, so running
it twice is safe. Ask an agent to run it in your terminal (or run it yourself in Cloud Shell):

```bash
PROJECT_ID=<project id> PROJECT_NUMBER=<project number> ALERT_EMAIL=<your e-mail> \
  bash scripts/backup-gcp-setup.sh
```

Read each command before you answer `y`; Google Cloud changes are yours to approve.

### 5. Check that the writer really is create-only

The script ends by printing four commands. Run them once: the first uploads a test file as the
writer and must succeed; reading, overwriting and deleting that file as the writer must each be
refused with HTTP 403. Then delete the test file as yourself. This is the check that the
"cannot destroy backups" promise is true, not just intended.

### 6. Run the first backup and look at it

Actions tab → `db-backup.yml` → Run workflow. When it is green, `gcloud storage ls
gs://meal-magician-backups/daily/` shows one file. Then do the optional 15-minute check below
once, and look at the bucket's cost after the first month (expect cents; the budget alert from
Hosting Setup covers it).

## Restore recipes

All three start the same way: pick a backup, then pick where to restore it.

Get your private key out of the password manager into a temporary file only you can read, and
remove it when you are done:

```bash
umask 077
cat > /tmp/mm-key.txt        # paste the key, then press Ctrl-D
```

The tool is `task db:restore` (it runs `scripts/db-restore.sh`). It does three things: fetches
the backup (a `gs://` name is copied with `gcloud`; a file on disk is used as is), decrypts it
with `age --decrypt` using the key file you point to, and runs `pg_restore` into the target. The
decrypted copy exists only in a private temporary folder that is deleted when the tool ends,
even if it fails. It needs `age` and, for the Neon targets, `pg_restore` of PostgreSQL 17 on your
machine.

`pg_restore` is run with `--clean --if-exists`: it **drops** each table in the backup before
recreating it. That is right for an empty or throw-away target and exactly why the tool asks
before touching a database that already has data. Exit codes: 0 restored, 1 a step failed, 2
nothing was done (wrong arguments, or you did not confirm).

### Recipe A: into the local Postgres (to look, or to test the backup)

Use this to check a backup, or to work on a copy of production data. It never touches Neon.

```bash
docker compose down --volumes     # throws away the local database
task up                           # a fresh, empty Postgres (waits until healthy)
task db:restore -- gs://meal-magician-backups/daily/<object-name> --target local --key /tmp/mm-key.txt
task serve:pg                     # starts the app on the restored data
```

Sign in with Google on <http://localhost:8080/> with an account on the allowlist and look at a
few plans. If the local database already holds tables, the tool refuses and asks for `--yes`.
When done: `rm /tmp/mm-key.txt`.

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
   task db:restore -- gs://meal-magician-backups/release/<object-name> --target neon-branch --key /tmp/mm-key.txt
   ```

   The tool shows the host and database and waits for you to type `yes`. Read the host: it must
   be the new branch, **not** the live endpoint.
3. Point the app at the branch (the tool prints this list but changes nothing itself): set the
   GitHub variables `DB_URL` (new host) and `DB_USERNAME` if they differ, put the branch's
   password into the `db-password` secret ([[Hosting Setup]], "Rotating a secret") and redeploy
   by running the release workflow.
4. Sign in and check the data. Once happy, make the branch the project's main one in Neon (or
   keep using it) and keep the old branch for a few days before deleting it.
5. `unset RESTORE_DB_URL; rm /tmp/mm-key.txt`

### Recipe C: into a new Neon project (the project itself is gone or unusable)

1. Recreate the project as in [[Hosting Setup]] part 4 (Postgres 17, Frankfurt, database
   `mealmagician`, connection pooling off). Do **not** deploy yet.
2. Restore exactly like recipe B, step 2, with `--target neon-project` and the new project's URL.
3. Everything that named the old database now needs the new one: `DB_URL`, `DB_USERNAME`, the
   `db-password` secret, then redeploy. The read-only role `mm_backup` does not exist in the new
   project (roles are not in a dump): create it again with the SQL from the setup script's step 4,
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
| Your private key matches the public key in `BACKUP_AGE_RECIPIENT` | The job only ever has the public key | The `age-keygen -y` comparison in setup step 1, done once |
| The app starts and works on the restored data | The job checks tables and counts, not the application | The optional check below |
| The job keeps running | A job that never starts leaves no failed run | The "no backup for two days" alert |

You declined a recurring manual drill, so none is required. If you want one anyway, this takes
about 15 minutes and is worth doing once after setup and again after any big change to the
database:

1. Recipe A with the newest `daily/` backup.
2. `task serve:pg`, sign in, open this week's plan and a template.
3. If it works, delete the key file and you are done; if not, treat it as a failed backup and
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
