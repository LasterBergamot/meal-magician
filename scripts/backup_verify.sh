#!/usr/bin/env bash
# Test-restores a backup dump into a throw-away Postgres and compares it with what the live
# database held when the dump was taken (db-backup.yml, #62). A dump that cannot be restored, or
# restores to something different, is no backup, so any problem exits 1 and fails the nightly run.
#
# It never talks to the live database. backup_dump.sh recorded, in the same snapshot as the dump,
# the Flyway version and every table's row count in "$DUMP_FILE.counts" (override: COUNTS_FILE).
# Comparing with the live database *now* would fail whenever someone wrote to it while the job ran.
#
# The workflow runs this inside a postgres:17 client container on the host network, so the client
# matches the server. Everything comes in through the environment (the password never appears on a
# command line):
#   DUMP_FILE                                   the custom-format dump made by backup_dump.sh
#   TGT_HOST TGT_USER TGT_PASSWORD TGT_DB       the empty throw-away database (a superuser: the
#                                               dump creates the `vector` extension)
# Optional: TGT_PORT (5432), TGT_SSLMODE (prefer), COUNTS_FILE, and PSQL / PG_RESTORE (the
# binaries, so tests can point at fakes).
#
# Checks, after the restore:
#   1. the highest successful Flyway version in the restore equals the recorded one (and is not empty);
#   2. every recorded table (schema `public`, `spring_session*` excluded: their rows are not dumped)
#      has the recorded row count in the restore.
#
# The log is public: this prints table names and "equal" / "differs" only. Never a row, a count, a
# host name or a password. pg_restore's own messages can quote row data (a failed COPY does), so they
# are discarded; reproduce a failure locally with the restore guide.
# Exit codes: 0 equal, 1 the restore failed or something differs, 2 a required variable is missing.
set -euo pipefail

for name in DUMP_FILE TGT_HOST TGT_USER TGT_PASSWORD TGT_DB; do
  if [[ -z "${!name:-}" ]]; then
    echo "backup_verify.sh: the environment variable $name is required." >&2
    exit 2
  fi
done

counts_file="${COUNTS_FILE:-$DUMP_FILE.counts}"
if [[ ! -s "$counts_file" ]]; then
  echo "FAIL: the recorded counts of the dump are missing (backup_dump.sh writes them)." >&2
  exit 1
fi

export PGHOST="$TGT_HOST" PGPORT="${TGT_PORT:-5432}" PGUSER="$TGT_USER" PGPASSWORD="$TGT_PASSWORD"
export PGDATABASE="$TGT_DB" PGSSLMODE="${TGT_SSLMODE:-prefer}"
psql_bin="${PSQL:-psql}"
pg_restore_bin="${PG_RESTORE:-pg_restore}"

# query <sql>: prints the bare result. Errors are discarded because they can name the host.
query() {
  "$psql_bin" --no-psqlrc --quiet --tuples-only --no-align -v ON_ERROR_STOP=1 \
    --command "$1" 2>/dev/null
}

echo "Restoring the dump into the throw-away database…"
if ! "$pg_restore_bin" --no-owner --no-privileges --exit-on-error \
  --dbname "$TGT_DB" "$DUMP_FILE" >/dev/null 2>&1; then
  echo "FAIL: pg_restore could not restore the dump (its messages are hidden because they can contain data)." >&2
  exit 1
fi

failed=0
checked=0
while IFS=$'\t' read -r table expected; do
  [[ -z "$table" ]] && continue
  if [[ "$table" == "__flyway_version__" ]]; then
    if [[ -n "$expected" ]] && actual=$(query 'SELECT max(version::int) FROM flyway_schema_history WHERE success') &&
      [[ "$actual" == "$expected" ]]; then
      echo "ok: Flyway version is equal"
    else
      echo "FAIL: the Flyway version differs or is empty between the live database and the restore." >&2
      failed=1
    fi
    continue
  fi
  checked=$((checked + 1))
  quoted="\"${table//\"/\"\"}\""
  if actual=$(query "SELECT count(*) FROM public.$quoted") && [[ "$actual" == "$expected" ]]; then
    echo "ok: $table row count is equal"
  else
    echo "FAIL: $table row count differs or could not be read." >&2
    failed=1
  fi
done <"$counts_file"

if ((checked == 0)); then
  echo "FAIL: the recorded counts list no tables." >&2
  exit 1
fi
if ((failed)); then
  exit 1
fi
echo "Restore verified."
