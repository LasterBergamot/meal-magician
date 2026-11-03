package hu.lasterbergamot.mealmagician;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code scripts/backup_dump.sh} and {@code scripts/backup_verify.sh} are the two halves of the
 * nightly backup (#62). They run here against fake {@code pg_dump}, {@code pg_restore} and {@code
 * psql} programs put first on the {@code PATH}, so no database is needed. The log of the real
 * workflow is public, so besides the exit codes these tests check that the password, the host name
 * and row data never reach the output, even when the fake tools print them.
 *
 * <p>The dump script reads the Flyway version and the row counts in the same snapshot as the dump
 * and the verify script compares the restore with those recorded values, never with the live
 * database; {@link #aWriteToTheLiveDatabaseAfterTheDumpDoesNotFailTheVerification()} pins that.
 */
class BackupScriptsTest {

  private static final Path DUMP_SCRIPT = Path.of("scripts/backup_dump.sh").toAbsolutePath();
  private static final Path VERIFY_SCRIPT = Path.of("scripts/backup_verify.sh").toAbsolutePath();

  private static final String PASSWORD = "s3cr3t-pw-value";
  private static final String HOST = "ep-quiet-forest.eu-central-1.example.test";
  private static final String ROW_DATA = "anna@example.com";

  @TempDir Path temp;

  private Path bin;

  @BeforeEach
  void fakeTools() throws IOException {
    bin = Files.createDirectory(temp.resolve("bin"));
    // Appends its arguments to $FAKE_LOG. Fails the first $FAKE_FAIL_FIRST times (a database that
    // is still waking up), or always when FAKE_DUMP_EXIT is set. Its error text holds every
    // secret the script must scrub.
    fake(
        "pg_dump",
        """
        #!/usr/bin/env bash
        echo "pg_dump $@" >>"$FAKE_LOG"
        count_file="$FAKE_LOG.count"
        count=$(cat "$count_file" 2>/dev/null || echo 0)
        echo $((count + 1)) >"$count_file"
        if [[ -n "${FAKE_DUMP_EXIT:-}" || $count -lt ${FAKE_FAIL_FIRST:-0} ]]; then
          echo "connection to server at \\"$PGHOST\\" (10.1.2.3), port 5432 failed: password $PGPASSWORD" >&2
          exit "${FAKE_DUMP_EXIT:-1}"
        fi
        while [[ $# -gt 0 ]]; do
          if [[ $1 == --file ]]; then printf 'abc' >"$2"; fi
          shift
        done
        """);
    fake(
        "pg_restore",
        """
        #!/usr/bin/env bash
        echo "pg_restore $@" >>"$FAKE_LOG"
        if [[ -n "${FAKE_RESTORE_FAIL:-}" ]]; then
          echo 'COPY failed for table "member": invalid input, line 1: anna@example.com' >&2
          exit 1
        fi
        """);
    // Plays both roles of psql. With --command (backup_verify.sh) it answers the restored
    // database's queries; the live database is told apart by PGHOST and logged, because the verify
    // script must never contact it. With the script on stdin (backup_dump.sh) it mimics the lines
    // the dump script sends: \\setenv, \\!, \\o and the count query, whose answer goes to the \\o
    // file. A failing connection is FAKE_PSQL_EXIT.
    fake(
        "psql",
        """
        #!/usr/bin/env bash
        echo "psql host=${PGHOST:-} args=$*" >>"$FAKE_LOG"
        sql=""
        for ((i = 1; i <= $#; i++)); do
          if [[ ${!i} == --command ]]; then j=$((i + 1)); sql=${!j}; fi
        done
        if [[ -n "$sql" ]]; then
          case "$sql" in
            *flyway_schema_history*) echo "${FAKE_TGT_VERSION-12}" ;;
            *"count(*)"*)
              table=$(echo "${sql##*public.}" | tr -d '"')
              if [[ $table == "${FAKE_DIFF_TABLE:-}" ]]; then echo 4; else echo 3; fi ;;
          esac
          exit 0
        fi
        if [[ -n "${FAKE_PSQL_EXIT:-}" ]]; then
          echo "psql: error: connection to server at \\"$PGHOST\\" failed: password $PGPASSWORD" >&2
          exit "$FAKE_PSQL_EXIT"
        fi
        out=/dev/null
        saw_snapshot=""
        while IFS= read -r line; do
          case "$line" in
            "SELECT pg_export_snapshot()"*) saw_snapshot=1 ;;
            '\\setenv MM_SNAPSHOT :snap') export MM_SNAPSHOT=fake-snapshot ;;
            '\\!'*) [[ -n "$saw_snapshot" ]] && sh -c "${line#??}" ;;
            "\\o '"*) out=${line#"\\o '"}; out=${out%"'"} ;;
            '\\o') out=/dev/null ;;
            *query_to_xml*)
              printf '__flyway_version__\\t%s\\nmeal\\t3\\nplan\\t3\\n' "${FAKE_SRC_VERSION-12}" >"$out" ;;
          esac
        done
        """);
  }

  // ---- backup_dump.sh ----

  @Test
  void theDumpReportsItsSizeAndLeavesOutSessionRows() throws Exception {
    Result result = run(DUMP_SCRIPT, dumpEnv());

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.output().trim()).isEqualTo("dumped 3 bytes");
    String log = Files.readString(temp.resolve("fake.log"));
    assertThat(log)
        .contains("pg_dump --format=custom", "--no-owner", "--no-privileges")
        .contains("--exclude-table-data=spring_session*")
        .contains("--file " + temp.resolve("out.dump"));
  }

  @Test
  void theDumpRunsInsideTheExportedSnapshotAndRecordsTheCountsOfThatSnapshot() throws Exception {
    Result result = run(DUMP_SCRIPT, dumpEnv());

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(Files.readString(temp.resolve("fake.log"))).contains("--snapshot=fake-snapshot");
    assertThat(Files.readString(temp.resolve("out.dump.counts")))
        .isEqualTo("__flyway_version__\t12\nmeal\t3\nplan\t3\n");
  }

  @Test
  void noPasswordIsEverPutOnACommandLine() throws Exception {
    run(DUMP_SCRIPT, dumpEnv());

    assertThat(Files.readString(temp.resolve("fake.log"))).doesNotContain(PASSWORD);
  }

  @Test
  void aFailingPgDumpFailsTheScriptAndLeaksNothing() throws Exception {
    Map<String, String> env = dumpEnv();
    env.put("FAKE_DUMP_EXIT", "1");

    Result result = run(DUMP_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("the dump failed");
    assertNothingSecret(result.output());
    assertThat(temp.resolve("out.dump.counts")).doesNotExist();
  }

  @Test
  void aFailingConnectionFailsTheScriptAndLeaksNothing() throws Exception {
    Map<String, String> env = dumpEnv();
    env.put("FAKE_PSQL_EXIT", "2");

    Result result = run(DUMP_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("<host>").contains("<password>");
    assertNothingSecret(result.output());
  }

  @Test
  void aDatabaseThatIsStillWakingUpIsRetried() throws Exception {
    Map<String, String> env = dumpEnv();
    env.put("FAKE_FAIL_FIRST", "2");
    env.put("CONNECT_ATTEMPTS", "3");

    Result result = run(DUMP_SCRIPT, env);

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.output().trim()).isEqualTo("dumped 3 bytes");
    assertNothingSecret(result.output());
  }

  @Test
  void theDumpNeedsItsPasswordAndNamesTheMissingVariable() throws Exception {
    Map<String, String> env = dumpEnv();
    env.remove("PGPASSWORD");

    Result result = run(DUMP_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("PGPASSWORD");
  }

  @Test
  void anOutputPathWithAQuoteIsRefused() throws Exception {
    Map<String, String> env = dumpEnv();
    env.put("OUT_FILE", temp.resolve("it's.dump").toString());

    Result result = run(DUMP_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("OUT_FILE");
  }

  // ---- backup_verify.sh ----

  @Test
  void aRestoreThatMatchesTheRecordedCountsPasses() throws Exception {
    Result result = run(VERIFY_SCRIPT, verifyEnv());

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.output()).contains("Restore verified.");
    assertNothingSecret(result.output());
    assertThat(Files.readString(temp.resolve("fake.log")))
        .contains("--no-owner", "--no-privileges", "--exit-on-error");
  }

  @Test
  void aWriteToTheLiveDatabaseAfterTheDumpDoesNotFailTheVerification() throws Exception {
    // The dump recorded meal=3 and plan=3. Afterwards the live database changes (the fake would
    // now answer 99), but the verification must neither ask the live database nor care.
    Map<String, String> env = verifyEnv();
    env.put("SRC_HOST", "live.example.test");
    env.put("FAKE_SRC_VERSION", "99");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(Files.readString(temp.resolve("fake.log"))).doesNotContain("live.example.test");
  }

  @Test
  void aDumpAndVerificationRunTogetherEvenIfTheLiveDatabaseChangesBetweenThem() throws Exception {
    Map<String, String> dump = dumpEnv();
    assertThat(run(DUMP_SCRIPT, dump).exit()).isZero();
    // The live database changes after the dump: the fake would now record other numbers.
    Map<String, String> verify = verifyEnv();
    verify.put("DUMP_FILE", temp.resolve("out.dump").toString());
    verify.put("FAKE_SRC_VERSION", "99");

    Result result = run(VERIFY_SCRIPT, verify);

    assertThat(result.exit()).as(result.output()).isZero();
  }

  @Test
  void differentFlywayVersionsFailTheVerification() throws Exception {
    Map<String, String> env = verifyEnv();
    env.put("FAKE_TGT_VERSION", "11");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("Flyway");
  }

  @Test
  void anEmptyFlywayHistoryFailsTheVerification() throws Exception {
    Map<String, String> env = verifyEnv();
    Files.writeString(temp.resolve("in.dump.counts"), "__flyway_version__\t\nmeal\t3\nplan\t3\n");
    env.put("FAKE_TGT_VERSION", "");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("Flyway");
  }

  @Test
  void aDifferentRowCountNamesTheTableButShowsNoData() throws Exception {
    Map<String, String> env = verifyEnv();
    env.put("FAKE_DIFF_TABLE", "plan");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("plan row count differs").contains("meal row count");
    assertThat(result.output()).doesNotContain("Restore verified.").doesNotContainPattern("\\d");
  }

  @Test
  void aFailedRestoreFailsTheVerificationWithoutShowingPgRestoreMessages() throws Exception {
    Map<String, String> env = verifyEnv();
    env.put("FAKE_RESTORE_FAIL", "1");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("pg_restore could not restore");
    assertNothingSecret(result.output());
  }

  @Test
  void missingRecordedCountsFailTheVerification() throws Exception {
    Map<String, String> env = verifyEnv();
    Files.delete(temp.resolve("in.dump.counts"));

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("recorded counts");
  }

  @Test
  void recordedCountsWithoutTablesFailTheVerification() throws Exception {
    Map<String, String> env = verifyEnv();
    Files.writeString(temp.resolve("in.dump.counts"), "\n__flyway_version__\t12\n");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("no tables");
  }

  @Test
  void theVerificationNamesAMissingVariable() throws Exception {
    Map<String, String> env = verifyEnv();
    env.remove("TGT_PASSWORD");

    Result result = run(VERIFY_SCRIPT, env);

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("TGT_PASSWORD");
  }

  // ---- helpers ----

  private static void assertNothingSecret(String output) {
    assertThat(output)
        .doesNotContain(PASSWORD)
        .doesNotContain(HOST)
        .doesNotContain("10.1.2.3")
        .doesNotContain(ROW_DATA);
  }

  private Map<String, String> dumpEnv() {
    Map<String, String> env = new HashMap<>();
    env.put("PGHOST", HOST);
    env.put("PGUSER", "mm_backup");
    env.put("PGDATABASE", "mealmagician");
    env.put("PGPASSWORD", PASSWORD);
    env.put("OUT_FILE", temp.resolve("out.dump").toString());
    env.put("CONNECT_ATTEMPTS", "1");
    env.put("CONNECT_PAUSE", "0");
    return env;
  }

  /** The dump and the counts the dump script would have recorded: meal and plan hold 3 rows. */
  private Map<String, String> verifyEnv() throws IOException {
    Path dump = Files.writeString(temp.resolve("in.dump"), "abc");
    Files.writeString(temp.resolve("in.dump.counts"), "__flyway_version__\t12\nmeal\t3\nplan\t3\n");
    Map<String, String> env = new HashMap<>();
    env.put("DUMP_FILE", dump.toString());
    env.put("TGT_HOST", "tgt.host");
    env.put("TGT_USER", "restore");
    env.put("TGT_DB", "restore");
    env.put("TGT_PASSWORD", PASSWORD);
    return env;
  }

  private void fake(String name, String script) throws IOException {
    Path file = Files.writeString(bin.resolve(name), script);
    assertThat(file.toFile().setExecutable(true)).isTrue();
  }

  private Result run(Path script, Map<String, String> env) throws Exception {
    ProcessBuilder builder = new ProcessBuilder("bash", script.toString()).directory(temp.toFile());
    builder.redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    // Nothing from the developer's own shell may leak into the script under test.
    environment.keySet().removeIf(key -> key.startsWith("PG") || key.matches("(SRC|TGT)_.*"));
    environment.put("PATH", bin + ":" + System.getenv("PATH"));
    environment.put("FAKE_LOG", temp.resolve("fake.log").toString());
    environment.putAll(env);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes());
    return new Result(process.waitFor(), output);
  }

  private record Result(int exit, String output) {}
}
