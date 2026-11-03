package hu.lasterbergamot.mealmagician;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code scripts/db-restore.sh} (the engine of {@code task db:restore}, issue #154) turns an
 * encrypted backup back into a database. A mistake in it shows up on the worst day, so it runs here
 * for real, in a bash process, against fake {@code age}, {@code pg_restore}, {@code docker} and
 * {@code gcloud} programs that sit first on the {@code PATH} and write what they were asked to do
 * into a log file. No real key, database or cloud is touched.
 *
 * <p>Besides the exit codes (0 restored, 1 a step failed, 2 nothing done), the tests check the part
 * that matters for security: the decrypted dump lives in a temporary directory that is empty again
 * after every run, successful or not, and no secret is ever printed.
 */
class DbRestoreScriptTest {

  private static final Path SCRIPT = Path.of("scripts/db-restore.sh").toAbsolutePath();
  private static final String NEON_URL =
      "postgresql://app_user:s3cret-password@ep-test.eu-central-1.aws.neon.tech/mealmagician"
          + "?sslmode=require";

  @TempDir Path dir;

  private Path bin;
  private Path scratch;
  private Path log;
  private Path key;
  private Path backup;

  @BeforeEach
  void fakeToolsAndFiles() throws IOException {
    bin = Files.createDirectories(dir.resolve("bin"));
    scratch = Files.createDirectories(dir.resolve("tmp"));
    log = dir.resolve("calls.log");
    Files.writeString(log, "");
    key = dir.resolve("key.txt");
    Files.writeString(key, "AGE-SECRET-KEY-NOT-A-REAL-KEY\n");
    backup = dir.resolve("backup.dump.age");
    Files.writeString(backup, "ENCRYPTED");

    // age: record the call, fail on request, otherwise "decrypt" by writing the file after -o.
    fake(
        "age",
        """
        echo "age $*" >> "$FAKE_LOG"
        [[ ${FAKE_AGE_FAIL:-} == 1 ]] && exit 1
        out=""
        while (($#)); do [[ $1 == -o ]] && out=$2; shift; done
        echo PLAINTEXT-DUMP > "$out"
        exit 0
        """);
    // pg_restore on the host (the Neon targets): record the call and whether the dump file existed.
    fake(
        "pg_restore",
        """
        echo "pg_restore $*" >> "$FAKE_LOG"
        last="${@: -1}"
        [[ -f $last ]] && echo "dump-file-existed-during-restore" >> "$FAKE_LOG"
        [[ ${FAKE_PG_FAIL:-} == 1 ]] && exit 1
        exit 0
        """);
    // docker compose exec ... (the local target): psql answers a count or a version, pg_restore
    // reads the dump from stdin.
    fake(
        "docker",
        """
        echo "docker $*" >> "$FAKE_LOG"
        case "$*" in
          *psql*information_schema*)
            [[ ${FAKE_DOCKER_DOWN:-} == 1 ]] && exit 1
            echo "${FAKE_TABLES:-0}"
            ;;
          *psql*flyway_schema_history*) echo 11 ;;
          *pg_restore*)
            grep -q PLAINTEXT-DUMP && echo "dump-on-stdin" >> "$FAKE_LOG"
            [[ ${FAKE_PG_FAIL:-} == 1 ]] && exit 1
            ;;
        esac
        exit 0
        """);
    // gcloud storage cp SRC DST: record and write an "encrypted" file, or fail on request.
    fake(
        "gcloud",
        """
        echo "gcloud $*" >> "$FAKE_LOG"
        [[ ${FAKE_GCLOUD_FAIL:-} == 1 ]] && exit 1
        echo ENCRYPTED > "${@: -1}"
        exit 0
        """);
  }

  @Test
  void noArgumentsPrintUsageAndExitTwo() throws Exception {
    Result result = run(List.of(), Map.of(), "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("usage: db-restore.sh");
  }

  @Test
  void anUnknownTargetIsRefused() throws Exception {
    Result result = run(List.of(backup.toString(), "--target", "nowhere"), Map.of(), "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("unknown target 'nowhere'");
  }

  @Test
  void aMissingTargetOrSourceOrValueIsRefused() throws Exception {
    assertThat(run(List.of(backup.toString()), Map.of(), "").exit()).isEqualTo(2);
    assertThat(run(List.of("--target", "local"), Map.of(), "").exit()).isEqualTo(2);
    assertThat(run(List.of(backup.toString(), "--target"), Map.of(), "").exit()).isEqualTo(2);
    assertThat(run(List.of(backup.toString(), "--key"), Map.of(), "").exit()).isEqualTo(2);
    assertThat(run(List.of(backup.toString(), "other", "--target", "local"), Map.of(), "").exit())
        .isEqualTo(2);
    assertThat(run(List.of(backup.toString(), "--bogus"), Map.of(), "").exit()).isEqualTo(2);
  }

  @Test
  void helpPrintsUsageAndExitsZero() throws Exception {
    Result result = run(List.of("--help"), Map.of(), "");

    assertThat(result.exit()).isZero();
    assertThat(result.output()).contains("usage: db-restore.sh");
  }

  @Test
  void restoresAnEncryptedBackupIntoTheLocalDatabaseAndRemovesTheDecryptedCopy() throws Exception {
    Result result = run(local(), Map.of(), "");

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.calls())
        .contains("age --decrypt -i " + key + " -o ")
        .contains("pg_restore --clean --if-exists --no-owner --no-privileges -U mealmagician")
        .contains("-d mealmagician")
        .contains("dump-on-stdin");
    assertThat(result.output()).contains("task serve:pg").contains("Newest Flyway migration");
    assertThat(result.output()).doesNotContain("AGE-SECRET-KEY");
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aLocalDatabaseThatAlreadyHasTablesIsOnlyReplacedWithYes() throws Exception {
    Map<String, String> populated = Map.of("FAKE_TABLES", "5");

    Result refused = run(local(), populated, "");
    assertThat(refused.exit()).isEqualTo(2);
    assertThat(refused.output()).contains("already has 5 tables").contains("--yes");
    assertThat(refused.calls()).doesNotContain("pg_restore");
    assertThat(scratchFiles()).isEmpty();

    List<String> withYes = new ArrayList<>(local());
    withYes.add("--yes");
    Result accepted = run(withYes, populated, "");
    assertThat(accepted.exit()).as(accepted.output()).isZero();
    assertThat(accepted.calls()).contains("pg_restore --clean");
  }

  @Test
  void aStoppedLocalDatabaseGivesExitOneAndCleansUp() throws Exception {
    Result result = run(local(), Map.of("FAKE_DOCKER_DOWN", "1"), "");

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("task up");
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aFailingPgRestoreExitsOneAndStillRemovesTheDecryptedCopy() throws Exception {
    Result local = run(local(), Map.of("FAKE_PG_FAIL", "1"), "");
    assertThat(local.exit()).isEqualTo(1);
    assertThat(local.output()).contains("pg_restore failed");
    assertThat(scratchFiles()).isEmpty();

    Result neon =
        run(
            neon("neon-branch", "--yes"),
            Map.of("FAKE_PG_FAIL", "1", "RESTORE_DB_URL", NEON_URL),
            "");
    assertThat(neon.exit()).isEqualTo(1);
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aKeyThatCannotDecryptExitsOneAndPointsAtTheKeyCheck() throws Exception {
    Result result = run(local(), Map.of("FAKE_AGE_FAIL", "1"), "");

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("could not decrypt").contains("age-keygen -y");
    assertThat(result.calls()).doesNotContain("pg_restore");
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aKeyFileThatDoesNotExistIsRefused() throws Exception {
    Result result =
        run(
            List.of(
                backup.toString(), "--target", "local", "--key", dir.resolve("nope").toString()),
            Map.of(),
            "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("does not exist");
    assertThat(result.calls()).isEmpty();
  }

  @Test
  void anEncryptedBackupNeedsAKey() throws Exception {
    Result result = run(List.of(backup.toString(), "--target", "local"), Map.of(), "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("--key FILE");
  }

  @Test
  void theKeyCanComeFromTheEnvironment() throws Exception {
    Result result =
        run(
            List.of(backup.toString(), "--target", "local"),
            Map.of("AGE_KEY_FILE", key.toString()),
            "");

    assertThat(result.exit()).as(result.output()).isZero();
  }

  @Test
  void aSourceThatIsNeitherAFileNorAGsObjectIsRefused() throws Exception {
    Result result =
        run(List.of(dir.resolve("missing.dump").toString(), "--target", "local"), Map.of(), "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("is not a file");
  }

  @Test
  void aNeonTargetNeedsRestoreDbUrl() throws Exception {
    Result result = run(neon("neon-branch"), Map.of(), "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("RESTORE_DB_URL");
    assertThat(result.calls()).isEmpty();
  }

  @Test
  void restoreDbUrlMustBeAPostgresUrl() throws Exception {
    Result result =
        run(neon("neon-project", "--yes"), Map.of("RESTORE_DB_URL", "mysql://host/db"), "");

    assertThat(result.exit()).isEqualTo(2);
    assertThat(result.output()).contains("must start with postgresql://");
  }

  @Test
  void restoresIntoANeonProjectAndNeverPrintsTheUrlsPassword() throws Exception {
    Result result = run(neon("neon-project", "--yes"), Map.of("RESTORE_DB_URL", NEON_URL), "");

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.calls())
        .contains("pg_restore --clean --if-exists --no-owner --no-privileges -d " + NEON_URL)
        .contains("dump-file-existed-during-restore");
    assertThat(result.output())
        .contains("ep-test.eu-central-1.aws.neon.tech/mealmagician")
        .contains("db-password")
        .doesNotContain("s3cret-password")
        .doesNotContain("app_user");
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aNeonRestoreAsksForConfirmationAndStopsWhenDeclined() throws Exception {
    Map<String, String> env = Map.of("RESTORE_DB_URL", NEON_URL);

    Result declined = run(neon("neon-branch"), env, "no\n");
    assertThat(declined.exit()).isEqualTo(2);
    assertThat(declined.output()).contains("Type yes").contains("nothing was changed");
    assertThat(declined.calls()).doesNotContain("pg_restore");
    assertThat(scratchFiles()).isEmpty();

    Result noAnswerAtAll = run(neon("neon-branch"), env, "");
    assertThat(noAnswerAtAll.exit()).isEqualTo(2);

    Result confirmed = run(neon("neon-branch"), env, "yes\n");
    assertThat(confirmed.exit()).as(confirmed.output()).isZero();
    assertThat(confirmed.calls()).contains("pg_restore --clean");
  }

  @Test
  void fetchesAGsObjectBeforeDecryptingIt() throws Exception {
    String object = "gs://meal-magician-backups/daily/2026-11-03T02-17.dump.age";

    Result result =
        run(List.of(object, "--target", "local", "--key", key.toString()), Map.of(), "");

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.calls()).contains("gcloud storage cp " + object).contains("age --decrypt");
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aFailedFetchExitsOne() throws Exception {
    Result result =
        run(
            List.of(
                "gs://meal-magician-backups/daily/x.age",
                "--target",
                "local",
                "--key",
                key.toString()),
            Map.of("FAKE_GCLOUD_FAIL", "1"),
            "");

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.output()).contains("could not fetch");
    assertThat(scratchFiles()).isEmpty();
  }

  @Test
  void aFileWithoutTheAgeSuffixIsTakenAsAPlainDump() throws Exception {
    Path plain = dir.resolve("backup.dump");
    Files.writeString(plain, "PLAINTEXT-DUMP");

    Result result = run(List.of(plain.toString(), "--target", "local"), Map.of(), "");

    assertThat(result.exit()).as(result.output()).isZero();
    assertThat(result.calls()).doesNotContain("age ").contains("dump-on-stdin");
    assertThat(Files.readString(plain)).isEqualTo("PLAINTEXT-DUMP");
  }

  // ------------------------------------------------------------------------------------------

  private List<String> local() {
    return List.of(backup.toString(), "--target", "local", "--key", key.toString());
  }

  private List<String> neon(String target, String... extra) {
    List<String> args = new ArrayList<>(local());
    args.set(2, target);
    args.addAll(List.of(extra));
    return args;
  }

  private void fake(String name, String body) throws IOException {
    Path file = bin.resolve(name);
    Files.writeString(file, "#!/usr/bin/env bash\n" + body);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  /** What is left in the directory the script was told to use for temporary files. */
  private List<Path> scratchFiles() throws IOException {
    try (Stream<Path> files = Files.list(scratch)) {
      return files.toList();
    }
  }

  private Result run(List<String> args, Map<String, String> env, String stdin) throws Exception {
    List<String> command = new ArrayList<>(List.of("bash", SCRIPT.toString()));
    command.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
    builder.redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    environment.put("PATH", bin + ":" + System.getenv("PATH"));
    environment.put("TMPDIR", scratch.toString());
    environment.put("FAKE_LOG", log.toString());
    environment.remove("RESTORE_DB_URL");
    environment.remove("AGE_KEY_FILE");
    environment.putAll(new HashMap<>(env));
    Process process = builder.start();
    process.getOutputStream().write(stdin.getBytes());
    process.getOutputStream().close();
    String output = new String(process.getInputStream().readAllBytes());
    int exit = process.waitFor();
    String calls = Files.readString(log);
    Files.writeString(log, "");
    return new Result(exit, output, calls);
  }

  private record Result(int exit, String output, String calls) {}
}
