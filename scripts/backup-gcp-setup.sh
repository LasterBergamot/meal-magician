#!/usr/bin/env bash
# One-time Google Cloud setup for the database backups (issue #154, PLAN.md section 3, stage 6a).
#
# Creates what the nightly backup workflow (db-backup.yml, #62) needs on the Google side:
#   - the private bucket meal-magician-backups (europe-west3, uniform access, public access
#     prevention enforced) and its lifecycle rules: daily/ 7 days, monthly/ 190 days, release/ 14 days
#   - the service account mm-backup-writer: it may CREATE objects in that bucket and read the one secret
#     backup-db-password, nothing else. It cannot read, overwrite or delete a backup, so a stolen
#     GitHub token cannot destroy the backups.
#   - the Workload Identity binding that lets GitHub Actions of THIS repository act as that account
#   - an e-mail alert that fires when no new object has reached the bucket for two days, so a
#     backup job that silently stopped (GitHub disables schedules after 60 quiet days) is noticed
# The Neon read-only role and the secret backup-db-password are NOT created by this script. It
# prints the SQL and the command, and waits while you run them yourself (the password is typed into
# your own terminal and never passes through this script).
#
# How it runs: every change is a "step". A step prints its description and the exact command, asks
# `Run this? [y/N]` and runs it only on `y`; anything else (including end of input) skips it, and
# the script then stops at the end saying which steps were skipped. Before each step the script
# looks (read-only) whether the thing already exists and skips it with a note if so, so running it
# twice is safe. It is meant to be run by an agent in the owner's terminal with the owner answering
# every prompt (CLAUDE.md: Google Cloud changes need the owner's OK).
#
# Environment (all required, none has a default except REGION, REPO and BUCKET):
#   PROJECT_ID       the Google Cloud project id
#   PROJECT_NUMBER   its number (Hosting-Setup.md part 1 shows both)
#   ALERT_EMAIL      where the "no backup for two days" e-mail goes
#   REGION           default europe-west3
#   REPO             default LasterBergamot/meal-magician (the only repository GitHub may act for)
#   BUCKET           default meal-magician-backups
#   GCLOUD           default gcloud (tests point it at a fake)
# Run:  PROJECT_ID=... PROJECT_NUMBER=... ALERT_EMAIL=... bash scripts/backup-gcp-setup.sh
# Exit status: 0 every step ran or was already done, 1 a command failed, 2 a setting is missing,
# 3 finished but some steps were skipped (nothing is wrong, run it again to do them).
set -euo pipefail

PROJECT_ID=${PROJECT_ID:-}
PROJECT_NUMBER=${PROJECT_NUMBER:-}
ALERT_EMAIL=${ALERT_EMAIL:-}
REGION=${REGION:-europe-west3}
REPO=${REPO:-LasterBergamot/meal-magician}
BUCKET=${BUCKET:-meal-magician-backups}
GCLOUD=${GCLOUD:-gcloud}

for name in PROJECT_ID PROJECT_NUMBER ALERT_EMAIL REGION REPO BUCKET; do
  if [[ -z ${!name} ]]; then
    echo "backup-gcp-setup: $name is not set (see the header of this script)" >&2
    exit 2
  fi
done

WRITER_NAME=mm-backup-writer
WRITER=$WRITER_NAME@$PROJECT_ID.iam.gserviceaccount.com
SECRET=backup-db-password
POLICY_NAME="Meal Magician: no database backup for 2 days"
CHANNEL_NAME="Meal Magician backup alert e-mail"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

skipped=()

# Prints a heading for a group of steps.
section() { printf '\n== %s ==\n' "$1"; }

# have DESCRIPTION COMMAND...: a read-only look. True when COMMAND succeeds, which means the thing
# already exists. The command is printed, never asked about, because it changes nothing.
have() {
  local description=$1
  shift
  printf '   (checking: %s)\n' "$description"
  "$@" >/dev/null 2>&1
}

# step DESCRIPTION COMMAND...: show the command, ask, and run it only after a `y`.
step() {
  local description=$1 answer=""
  shift
  printf '\n-> %s\n   $' "$description"
  printf ' %q' "$@"
  printf '\n'
  printf '   Run this? [y/N] '
  read -r answer || answer=""
  echo
  if [[ $answer == y || $answer == Y ]]; then
    "$@"
  else
    echo "   skipped"
    skipped+=("$description")
  fi
}

# step_unless DESCRIPTION CHECK_DESCRIPTION -- CHECK... -- COMMAND...: run the check; when it succeeds
# the thing exists, say so, otherwise fall through to `step`.
step_unless() {
  local description=$1 check_description=$2
  shift 2
  [[ $1 == -- ]] && shift
  local check=()
  while (($#)) && [[ $1 != -- ]]; do
    check+=("$1")
    shift
  done
  [[ ${1:-} == -- ]] && shift
  if have "$check_description" "${check[@]}"; then
    printf '   already there: %s\n' "$description"
  else
    step "$description" "$@"
  fi
}

echo "Meal Magician backup setup for project $PROJECT_ID ($REGION), bucket gs://$BUCKET"
echo "Every step asks first. Press Enter or n to skip one."

# ---------------------------------------------------------------------------------------------
section "1. APIs"
# Cloud Storage holds the backups; Cloud Monitoring owns the alert. (The IAM, Secret Manager, STS
# and Run APIs were turned on by Hosting-Setup.md part 3.)
step "Turn on the Cloud Storage and Cloud Monitoring APIs" \
  "$GCLOUD" services enable storage.googleapis.com monitoring.googleapis.com --project="$PROJECT_ID"

# ---------------------------------------------------------------------------------------------
section "2. The bucket"
# --uniform-bucket-level-access: only IAM decides, no per-object ACLs that could expose one file.
# --public-access-prevention: nobody can ever make it public, even by mistake.
step_unless "Create the private bucket gs://$BUCKET in $REGION" \
  "does gs://$BUCKET exist" -- "$GCLOUD" storage buckets describe "gs://$BUCKET" --project="$PROJECT_ID" -- \
  "$GCLOUD" storage buckets create "gs://$BUCKET" --project="$PROJECT_ID" --location="$REGION" \
  --uniform-bucket-level-access --public-access-prevention

# matchesPrefix takes prefixes WITHOUT a leading slash. Google's own service agent runs the deletes,
# so the writer needs no delete right. Applying the same file again changes nothing, so no check.
cat >"$work/lifecycle.json" <<'EOF'
{
  "rule": [
    {"action": {"type": "Delete"}, "condition": {"age": 7, "matchesPrefix": ["daily/"]}},
    {"action": {"type": "Delete"}, "condition": {"age": 190, "matchesPrefix": ["monthly/"]}},
    {"action": {"type": "Delete"}, "condition": {"age": 14, "matchesPrefix": ["release/"]}}
  ]
}
EOF
echo
echo "The lifecycle rules (daily/ 7 days, monthly/ 190 days, release/ 14 days):"
cat "$work/lifecycle.json"
step "Apply the lifecycle rules to the bucket (replaces any earlier rules)" \
  "$GCLOUD" storage buckets update "gs://$BUCKET" --project="$PROJECT_ID" --lifecycle-file="$work/lifecycle.json"

# ---------------------------------------------------------------------------------------------
section "3. The writer service account"
step_unless "Create the service account $WRITER_NAME" \
  "does $WRITER exist" -- "$GCLOUD" iam service-accounts describe "$WRITER" --project="$PROJECT_ID" -- \
  "$GCLOUD" iam service-accounts create "$WRITER_NAME" --project="$PROJECT_ID" \
  --display-name="Meal Magician backup writer (create-only)"

# roles/storage.objectCreator has storage.objects.create and nothing else: no get, list, update
# or delete. Replacing an object needs delete, so the writer cannot overwrite either. Granted on
# the bucket, not the project.
step "Let $WRITER create objects in gs://$BUCKET (and nothing else there)" \
  "$GCLOUD" storage buckets add-iam-policy-binding "gs://$BUCKET" --project="$PROJECT_ID" \
  --member="serviceAccount:$WRITER" --role=roles/storage.objectCreator

# ---------------------------------------------------------------------------------------------
section "4. The Neon read-only role and its password secret (you do these two by hand)"
if ! have "does the secret $SECRET exist" "$GCLOUD" secrets describe "$SECRET" --project="$PROJECT_ID"; then
  cat <<EOF

The dump runs as a database role that can only READ. Create it in the Neon SQL editor (project
meal-magician, database mealmagician), as the role that owns the tables. Replace the two <...>
values: the password is a new long random one (for example from 'openssl rand -base64 24'), and
<app role> is the role the app connects as (the DB_USERNAME GitHub variable, often neondb_owner).
Default privileges only cover tables that <app role> creates later, which is how Flyway adds them.

  CREATE ROLE mm_backup LOGIN PASSWORD '<long random password>';
  GRANT CONNECT ON DATABASE mealmagician TO mm_backup;
  GRANT USAGE ON SCHEMA public TO mm_backup;
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO mm_backup;
  GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO mm_backup;
  ALTER DEFAULT PRIVILEGES FOR ROLE "<app role>" IN SCHEMA public GRANT SELECT ON TABLES TO mm_backup;
  ALTER DEFAULT PRIVILEGES FOR ROLE "<app role>" IN SCHEMA public GRANT SELECT ON SEQUENCES TO mm_backup;

Then store that same password in Secret Manager. Run this in YOUR OWN terminal (not through an
agent): it asks for the password without showing it.

  read -rsp 'mm_backup password: ' S && echo && \\
    printf '%s' "\$S" | $GCLOUD secrets create $SECRET --project=$PROJECT_ID --data-file=- && unset S

EOF
  read -rp "Press Enter when both are done (or Ctrl-C to stop and come back): " _ || true
  if ! have "does the secret $SECRET exist now" "$GCLOUD" secrets describe "$SECRET" --project="$PROJECT_ID"; then
    echo "backup-gcp-setup: the secret $SECRET still does not exist; create it and run this script again." >&2
    exit 1
  fi
fi

# Only this one secret, and only to read: the writer cannot see db-password or the Google client secret.
step "Let $WRITER read the secret $SECRET (this secret only)" \
  "$GCLOUD" secrets add-iam-policy-binding "$SECRET" --project="$PROJECT_ID" \
  --member="serviceAccount:$WRITER" --role=roles/secretmanager.secretAccessor

# ---------------------------------------------------------------------------------------------
section "5. GitHub may act as the writer (Workload Identity Federation)"
# The pool "github" and its provider already exist (Hosting-Setup.md part 3 step 5); the provider's
# attribute condition already admits only this repository, so a second service account needs only
# this binding. principalSet ... attribute.repository/$REPO is every workflow run of this repo.
step "Let GitHub Actions of $REPO act as $WRITER_NAME" \
  "$GCLOUD" iam service-accounts add-iam-policy-binding "$WRITER" --project="$PROJECT_ID" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"

# ---------------------------------------------------------------------------------------------
section "6. The silent-stop alert"
# Why this metric: storage.googleapis.com/api/request_count counts the requests the bucket serves,
# per method, and needs no logging to be switched on. The alternatives were worse: object_count is
# sampled once a day, lags, and also drops when a lifecycle rule deletes (so it cannot show "no new
# object"); a log-based metric on storage.objects.create needs Data Access audit logs, which are off by
# default and cost money to turn on.
# A metric-absence condition cannot wait longer than a day, so the condition is a threshold: the
# number of upload requests in the last 23 hours is below 1 (missing data counts as 0, hence
# EVALUATION_MISSING_DATA_ACTIVE) and that stays true for another 24 hours. The nightly backup keeps
# the 23-hour count at 1 or more; the count only stays 0 for 24 hours after about 47 hours without an
# upload, which is the "two days" of PLAN.md. See documentation/Backup-and-Restore.md, "If the alert fires".
cat >"$work/policy.json" <<EOF
{
  "displayName": "$POLICY_NAME",
  "combiner": "OR",
  "conditions": [
    {
      "displayName": "No upload to gs://$BUCKET for about 2 days",
      "conditionThreshold": {
        "filter": "metric.type=\"storage.googleapis.com/api/request_count\" AND resource.type=\"gcs_bucket\" AND resource.labels.bucket_name=\"$BUCKET\" AND metric.labels.method=monitoring.regex.full_match(\"(Insert|Write)Object\")",
        "aggregations": [
          {
            "alignmentPeriod": "82800s",
            "perSeriesAligner": "ALIGN_SUM",
            "crossSeriesReducer": "REDUCE_SUM"
          }
        ],
        "comparison": "COMPARISON_LT",
        "thresholdValue": 1,
        "duration": "86400s",
        "trigger": {"count": 1},
        "evaluationMissingData": "EVALUATION_MISSING_DATA_ACTIVE"
      }
    }
  ],
  "alertStrategy": {"autoClose": "604800s"},
  "documentation": {
    "mimeType": "text/markdown",
    "content": "No object has reached gs://$BUCKET for about two days. The nightly workflow db-backup.yml has probably stopped (GitHub disables schedules after 60 days without repository activity, or the job is failing). Open the repository's Actions tab, run the db-backup.yml workflow by hand, and read the wiki page 'Backup and Restore', section 'If the alert fires'."
  }
}
EOF
echo
echo "The alert policy:"
cat "$work/policy.json"

channel_filter="displayName=\"$CHANNEL_NAME\" AND type=\"email\""
step_unless "Create the e-mail notification channel for $ALERT_EMAIL" \
  "is there a channel named '$CHANNEL_NAME'" -- \
  bash -c '[[ -n $("$0" beta monitoring channels list --project="$1" --filter="$2" --format="value(name)") ]]' \
  "$GCLOUD" "$PROJECT_ID" "$channel_filter" -- \
  "$GCLOUD" beta monitoring channels create --project="$PROJECT_ID" --type=email \
  --display-name="$CHANNEL_NAME" --channel-labels=email_address="$ALERT_EMAIL"

channel=$("$GCLOUD" beta monitoring channels list --project="$PROJECT_ID" \
  --filter="$channel_filter" --format='value(name)' 2>/dev/null | head -n 1 || true)
if [[ -z $channel ]]; then
  # Nothing to attach the policy to yet (the channel step was skipped or failed): do not build a
  # policy that e-mails nobody.
  echo "   no notification channel yet, so the alert policy step is skipped; run this script again."
  skipped+=("Create the alert policy (needs the e-mail channel first)")
else
  step_unless "Create the alert policy '$POLICY_NAME'" \
    "is there a policy named '$POLICY_NAME'" -- \
    bash -c '[[ -n $("$0" monitoring policies list --project="$1" --filter="displayName=\"$2\"" --format="value(name)") ]]' \
    "$GCLOUD" "$PROJECT_ID" "$POLICY_NAME" -- \
    "$GCLOUD" monitoring policies create --project="$PROJECT_ID" \
    --policy-from-file="$work/policy.json" --notification-channels="$channel"
fi

# ---------------------------------------------------------------------------------------------
section "Done: check it once by hand"
cat <<EOF

Nothing above proves the writer is as limited as intended, and nothing has uploaded yet. Check
once, as yourself, with the writer's identity (you need the role Service Account Token Creator on
$WRITER for --impersonate-service-account; add it for yourself for the check and remove it after).
The first command must succeed, the next three must be refused (HTTP 403):

  echo canary | $GCLOUD storage cp - gs://$BUCKET/release/writer-check.txt --impersonate-service-account=$WRITER
  $GCLOUD storage cat gs://$BUCKET/release/writer-check.txt --impersonate-service-account=$WRITER
  echo again | $GCLOUD storage cp - gs://$BUCKET/release/writer-check.txt --impersonate-service-account=$WRITER
  $GCLOUD storage rm gs://$BUCKET/release/writer-check.txt --impersonate-service-account=$WRITER

Then delete the canary as yourself: $GCLOUD storage rm gs://$BUCKET/release/writer-check.txt
Record the four outcomes in the pull request. After the first real backup, open Metrics Explorer
(metric storage.googleapis.com/api/request_count, resource Cloud Storage Bucket) and check that
the upload shows up under a method matching (Insert|Write)Object; if it is named differently,
adjust the filter in the policy.
EOF

if ((${#skipped[@]})); then
  echo
  echo "Skipped steps (run the script again to do them):"
  printf '  - %s\n' "${skipped[@]}"
  exit 3
fi
echo
echo "Every step is done."
