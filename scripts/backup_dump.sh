#!/usr/bin/env bash
# Dumps the Meal Magician database to one custom-format file, and records what the database held at
# that exact moment (db-backup.yml, #62).
#
# The workflow runs this inside a digest-pinned postgres:17 container, so the pg_dump client
# matches the server major version. Everything comes in through the environment (the password
# never appears on a command line):
#   PGHOST, PGUSER, PGDATABASE, PGPASSWORD   the read-only role `mm_backup` on Neon's direct endpoint
#                                            (a pooled endpoint cannot share a snapshot)
#   PGPORT                                   default 5432
#   PGSSLMODE                                default require
#   OUT_FILE                                 where to write the dump
# Optional: PG_DUMP and PSQL (the binaries, so tests can point at fakes), CONNECT_ATTEMPTS (default
# 12) and CONNECT_PAUSE seconds (default 5): Neon scales to zero and the first connection can take a
# minute.
#
# Why one session: backup_verify.sh later compares the restored copy with "the live database", but
# the live database keeps changing (a member adds a meal while the job runs). So one psql session
# opens a REPEATABLE READ transaction and exports its snapshot; pg_dump is started from inside that
# same session with `--snapshot`, and the Flyway version and the row count of every table are read
# in that same transaction before it is committed. The dump and the counts therefore describe the
# same point in time. The counts go to "$OUT_FILE.counts" (one `name<TAB>value` line per table plus
# `__flyway_version__`); the file stays on the runner, is never uploaded and is deleted with the dump.
#
# The log is public, so this prints exactly one line, `dumped <bytes> bytes`, on success. On
# failure it prints a generic message and the error text with the host name, the password and IP
# addresses removed. Rows are never printed.
# Exit codes: 0 ok, 1 the dump failed, 2 a required variable is missing or OUT_FILE has a quote.
set -euo pipefail

for name in PGHOST PGUSER PGDATABASE PGPASSWORD OUT_FILE; do
  if [[ -z "${!name:-}" ]]; then
    echo "backup_dump.sh: the environment variable $name is required." >&2
    exit 2
  fi
done
if [[ "$OUT_FILE" == *"'"* ]]; then
  echo "backup_dump.sh: OUT_FILE must not contain a single quote." >&2
  exit 2
fi

export PGPORT="${PGPORT:-5432}"
export PGSSLMODE="${PGSSLMODE:-require}"
export OUT_FILE
psql_bin="${PSQL:-psql}"
attempts="${CONNECT_ATTEMPTS:-12}"
pause="${CONNECT_PAUSE:-5}"
counts_file="$OUT_FILE.counts"

errors=$(mktemp)
status=$(mktemp -u)
trap 'rm -f "$errors" "$status"' EXIT
# Read by the `\!` line of the psql script below (it runs in a shell started by psql).
export MM_PG_DUMP="${PG_DUMP:-pg_dump}" MM_ERRORS="$errors" MM_STATUS="$status"

# Removes what must not reach a public log from an error text.
scrub() {
  local text
  text=$(cat)
  text=${text//"$PGHOST"/<host>}
  text=${text//"$PGPASSWORD"/<password>}
  printf '%s\n' "$text" | sed -E 's/[0-9]{1,3}(\.[0-9]{1,3}){3}/<ip>/g'
}

# One statement, one line: the Flyway version, then every public table (except the session tables,
# whose rows are not dumped) with its row count. query_to_xml counts each table without having to
# know the names beforehand, inside the open transaction.
counts_sql="SELECT '__flyway_version__'::text, max(version::int)::text FROM flyway_schema_history WHERE success UNION ALL SELECT tablename::text, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', tablename), false, true, '')))[1]::text FROM pg_tables WHERE schemaname = 'public' AND tablename NOT LIKE 'spring\\_session%';"

attempt=1
while true; do
  rm -f "$status" "$OUT_FILE" "$counts_file"
  : >"$errors"
  # `\gset` stores the exported snapshot id in the psql variable `snap`; `\setenv` hands it to the
  # `\!` shell, which runs pg_dump while this transaction (and so the snapshot) is still open.
  # Output of the count query goes to the counts file only (`\o`); psql's own output is discarded.
  if "$psql_bin" --no-psqlrc --quiet --no-align --tuples-only --field-separator=$'\t' -v ON_ERROR_STOP=1 >/dev/null 2>>"$errors" <<EOF; then
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT pg_export_snapshot() AS snap \gset
\setenv MM_SNAPSHOT :snap
\! "\$MM_PG_DUMP" --format=custom --no-owner --no-privileges --exclude-table-data='spring_session*' --snapshot="\$MM_SNAPSHOT" --file "\$OUT_FILE" 2>>"\$MM_ERRORS" && echo ok >"\$MM_STATUS"
\o '$counts_file'
$counts_sql
\o
COMMIT;
EOF
    if [[ -f "$status" && -s "$OUT_FILE" && -s "$counts_file" ]]; then
      break
    fi
  fi
  if ((attempt >= attempts)); then
    echo "backup_dump.sh: the dump failed after $attempt attempt(s):" >&2
    scrub <"$errors" >&2
    rm -f "$OUT_FILE" "$counts_file"
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep "$pause"
done

echo "dumped $(wc -c <"$OUT_FILE" | tr -d ' ') bytes"
