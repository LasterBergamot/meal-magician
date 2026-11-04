#!/usr/bin/env bash
# Restore a database backup (`task db:restore -- <file-or-gs://object> --target ...`, issue #154).
#
# The nightly backup (db-backup.yml, #62) is a plain `pg_dump --format=custom` file named `*.dump`.
# It is not encrypted: see documentation/Backup-and-Restore.md, "Why the backups are not encrypted".
# This script turns such a file back into a running database in two steps:
#   1. fetch    a `gs://` object is copied with gcloud (sign in first with your own Google login);
#               a local file is used where it is and is never deleted;
#   2. restore  `pg_restore --clean --if-exists --no-owner --no-privileges` into the chosen target.
# A dump is the whole database in clear text (e-mail addresses, meals, rating comments). A copy this
# script fetches lives only in a private (mode 700) temporary directory that a trap removes on every
# way out, including a failure or Ctrl-C. A dump YOU downloaded yourself and pass as a file stays
# where it is: delete it after the restore. Nothing here prints a password or a connection string;
# only the host and database name of a Neon target are shown, so you can see where the data is about
# to go.
#
# Usage:
#   scripts/db-restore.sh <source> --target local|neon-branch|neon-project [--yes]
#
#   <source>   a file on disk or a gs://meal-magician-backups/... object (a name ending in `.age` is
#              refused: backups made before the encryption was dropped must be decrypted first)
#   --target   local          the compose Postgres (`task up`), database mealmagician
#              neon-branch    a NEW Neon branch you created for this, URL in RESTORE_DB_URL
#              neon-project   a NEW Neon project you created for this, URL in RESTORE_DB_URL
#   --yes      do not ask: restore into a local database that already has tables, or into a Neon
#              URL without asking you to confirm it
#
# The restore uses --clean, so it DROPS every object the dump contains before recreating it. That
# is what you want on a fresh target and the reason the script asks before touching a database
# that is not empty. Never point RESTORE_DB_URL at the live database by accident: the confirmation
# shows the host and database name, read it.
#
# Environment (optional): PG_RESTORE and GCLOUD name the programs to run (the tests put fakes
# there); RESTORE_DB_URL is the postgresql:// URL of a Neon target.
# Exit status: 0 restored, 1 a step failed (fetch or pg_restore), 2 nothing was done because the
# request was wrong or you declined (bad usage, a `.age` source, missing URL, not confirmed).
set -euo pipefail

PG_RESTORE=${PG_RESTORE:-pg_restore}
GCLOUD=${GCLOUD:-gcloud}

usage() {
  cat >&2 <<'EOF'
usage: db-restore.sh <file-or-gs://object> --target local|neon-branch|neon-project [--yes]
  --target local          restore into the compose Postgres (run `task up` first)
  --target neon-branch    restore into a new Neon branch; set RESTORE_DB_URL to its postgresql:// URL
  --target neon-project   restore into a new Neon project; set RESTORE_DB_URL to its postgresql:// URL
  --yes                   do not ask before overwriting
exit status: 0 restored, 1 a step failed, 2 bad usage or not confirmed
EOF
}

die_usage() {
  echo "db-restore: $1" >&2
  usage
  exit 2
}

source_arg=""
target=""
assume_yes=0
while (($#)); do
  case $1 in
    --target)
      (($# >= 2)) || die_usage "--target needs a value"
      target=$2
      shift 2
      ;;
    --yes | -y)
      assume_yes=1
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    -*) die_usage "unknown option $1" ;;
    *)
      [[ -z $source_arg ]] || die_usage "more than one source given"
      source_arg=$1
      shift
      ;;
  esac
done

[[ -n $source_arg ]] || die_usage "no backup file or gs:// object given"
case $target in
  local | neon-branch | neon-project) ;;
  "") die_usage "--target is required" ;;
  *) die_usage "unknown target '$target' (want local, neon-branch or neon-project)" ;;
esac

if [[ ${source_arg##*/} == *.age ]]; then
  die_usage "${source_arg##*/} ends in .age: backups are no longer encrypted. An old .age file must be decrypted with age first ('age --decrypt -i KEYFILE -o backup.dump ${source_arg##*/}'), then pass the .dump file."
fi
if [[ $source_arg != gs://* && ! -f $source_arg ]]; then
  die_usage "'$source_arg' is not a file (a Cloud Storage object starts with gs://)"
fi

target_label="the compose Postgres (database mealmagician)"
if [[ $target != local ]]; then
  if [[ -z ${RESTORE_DB_URL:-} ]]; then
    die_usage "RESTORE_DB_URL is not set: create the Neon $target yourself and export its postgresql:// URL as RESTORE_DB_URL"
  fi
  if [[ $RESTORE_DB_URL != postgres://* && $RESTORE_DB_URL != postgresql://* ]]; then
    die_usage "RESTORE_DB_URL must start with postgresql://"
  fi
  # Show only host and database: drop the scheme, the user:password@ part and the ?options.
  location=${RESTORE_DB_URL#*://}
  location=${location#*@}
  target_label="${location%%\?*} (Neon, $target)"
fi

umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
trap 'exit 130' INT TERM

# --- 1. fetch ----------------------------------------------------------------------------------
input=$source_arg
if [[ $source_arg == gs://* ]]; then
  input="$work/downloaded"
  echo "Fetching $source_arg ..."
  if ! "$GCLOUD" storage cp "$source_arg" "$input" >/dev/null; then
    echo "db-restore: could not fetch $source_arg (are you signed in with gcloud, and is the name right?)" >&2
    exit 1
  fi
fi

# --- 2. confirm and restore --------------------------------------------------------------------
if [[ $target == local ]]; then
  if ! tables=$(docker compose exec -T postgres psql -U mealmagician -d mealmagician -tA \
    -c "select count(*) from information_schema.tables where table_schema = 'public'"); then
    echo "db-restore: cannot reach the compose Postgres: run 'task up' first." >&2
    exit 1
  fi
  tables=${tables//[[:space:]]/}
  if [[ $tables != 0 ]] && ((!assume_yes)); then
    echo "db-restore: the local database already has $tables tables; restoring replaces them." >&2
    echo "            Start from an empty one ('docker compose down --volumes && task up') or add --yes." >&2
    exit 2
  fi
elif ((!assume_yes)); then
  echo "About to restore into $target_label. This DROPS and recreates every table in the dump there."
  printf 'Type yes to continue: '
  read -r answer || answer=""
  echo
  if [[ $answer != yes ]]; then
    echo "db-restore: not confirmed, nothing was changed." >&2
    exit 2
  fi
fi

echo "Restoring into $target_label ..."
if [[ $target == local ]]; then
  if ! docker compose exec -T postgres pg_restore --clean --if-exists --no-owner --no-privileges \
    -U mealmagician -d mealmagician <"$input"; then
    echo "db-restore: pg_restore failed (read its messages above)." >&2
    exit 1
  fi
  version=$(docker compose exec -T postgres psql -U mealmagician -d mealmagician -tA \
    -c "select version from flyway_schema_history where success order by installed_rank desc limit 1" 2>/dev/null || true)
  echo "Restored. Newest Flyway migration in the data: ${version//[$'\r\n']/ }"
  echo "Next: 'task serve:pg' starts the app on this data; sign in and look at a few plans."
else
  # The URL is on the command line, so other users of this machine could see it in `ps`; fine on
  # a personal laptop, do not run this on a shared host.
  if ! "$PG_RESTORE" --clean --if-exists --no-owner --no-privileges \
    -d "$RESTORE_DB_URL" "$input"; then
    echo "db-restore: pg_restore failed (read its messages above)." >&2
    exit 1
  fi
  echo "Restored into $target_label."
  echo "Next, by hand (this script does not touch the running service):"
  echo "  1. Point the GitHub variables DB_URL and DB_USERNAME at the restored database and store its"
  echo "     password in Secret Manager as db-password (documentation/Hosting-Setup.md, parts 5 and 6)."
  echo "  2. Redeploy by running the release workflow."
  echo "  3. Sign in and check the data."
fi
