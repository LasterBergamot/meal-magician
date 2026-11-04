package hu.lasterbergamot.mealmagician.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.assistant.eval.EvalOptions.UsageException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The runner's argument handling, exit codes and loop, with no Docker, Google or model. */
class EvalRunnerTest {

  @TempDir Path reports;

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
  private FakeEnvironment env;

  @BeforeEach
  void setUp() {
    env = new FakeEnvironment();
    env.reportDir = reports;
    env.answers = (c, run) -> result(c.id(), run, true, 1_000);
  }

  private static RunResult result(String caseId, int run, boolean pass, long cost) {
    return new RunResult(
        "x",
        caseId,
        run,
        List.of(
            new Checks.Named(
                "realIds", pass ? Checks.Result.ok("fine") : Checks.Result.fail("bad id 999"))),
        null,
        100,
        50,
        cost,
        1_500,
        false);
  }

  private int run(String... args) {
    return EvalRunner.run(args, out, err, env);
  }

  private String out() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  private String err() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void withoutModelsItExitsWithTwoAndPrintsTheUsage() {
    int code = run();

    assertThat(code).isEqualTo(2);
    assertThat(err()).contains("--models is required").contains("Usage: task ai:eval");
    assertThat(env.opened).isEmpty();
  }

  @Test
  void helpPrintsTheUsageAndTouchesNeitherDockerNorGoogle() {
    env.docker = false;
    env.googleProblem = "no login";

    int code = run("--help");

    assertThat(code).isZero();
    assertThat(out()).contains("Usage: task ai:eval").contains("--models").contains("Docker");
    assertThat(err()).isEmpty();
  }

  @Test
  void anUnknownCaseExitsWithTwo() {
    assertThat(run("--models", "a", "--cases", "nope", "--price-in", "a=1", "--price-out", "a=2"))
        .isEqualTo(2);
    assertThat(err()).contains("no case named nope");
  }

  @Test
  void aMissingPriceExitsWithTwoAndNamesTheModel() {
    assertThat(run("--models", "a")).isEqualTo(2);
    assertThat(err()).contains("no price for a").contains("--price-in a=");
  }

  @Test
  void pricesCanComeFromTheEnvironment() {
    env.variables = Map.of("AI_PRICE_IN", "1.65", "AI_PRICE_OUT", "9.90");

    assertThat(run("--models", "a", "--runs", "1", "--cases", "plain-week")).isZero();
    assertThat(env.pricesIn).containsExactly(new java.math.BigDecimal("1.65"));
  }

  @Test
  void aBrokenPriceInTheEnvironmentCountsAsMissing() {
    env.variables = Map.of("AI_PRICE_IN", "cheap", "AI_PRICE_OUT", "9.90");

    assertThat(run("--models", "a")).isEqualTo(2);
  }

  @Test
  void withoutDockerItSaysSoWithoutAStackTrace() {
    env.docker = false;

    assertThat(run("--models", "a", "--price-in", "a=1", "--price-out", "a=2")).isEqualTo(1);
    assertThat(err())
        .contains("Docker is not available")
        .doesNotContain("Exception")
        .doesNotContain("\tat ");
  }

  @Test
  void withoutAGoogleLoginItSaysWhatToDo() {
    env.googleProblem = "no Google login found. Run `gcloud auth application-default login`.";

    assertThat(run("--models", "a", "--price-in", "a=1", "--price-out", "a=2")).isEqualTo(1);
    assertThat(err())
        .contains("Cannot reach Google")
        .contains("gcloud auth application-default login");
    assertThat(env.opened).isEmpty();
  }

  @Test
  void runsEveryCaseTheGivenNumberOfTimesForEachModelAndWritesTheReport() throws IOException {
    int code =
        run("--models", "a,b", "--runs", "2", "--price-in", "a=1,b=1", "--price-out", "a=2,b=2");

    assertThat(code).isZero();
    assertThat(env.opened).containsExactly("a", "b");
    assertThat(out()).contains("# Assistant eval report").contains("Report written to");
    try (var files = Files.list(reports)) {
      List<Path> written = files.toList();
      assertThat(written).hasSize(1);
      assertThat(written.getFirst().getFileName().toString()).isEqualTo("20261104-101530.md");
      String report = Files.readString(written.getFirst());
      assertThat(report).contains("| a | 32 | 100% |").contains("| b | 32 | 100% |");
    }
  }

  @Test
  void aLessMeatRunAlsoRunsItsBaselineFirst() {
    List<String> order = new java.util.ArrayList<>();
    env.answers =
        (c, run) -> {
          order.add(c.id());
          return result(c.id(), run, true, 0);
        };

    run(
        "--models",
        "a",
        "--runs",
        "1",
        "--cases",
        "less-meat",
        "--price-in",
        "a=1",
        "--price-out",
        "a=2");

    assertThat(order).containsExactly("plain-week", "less-meat");
  }

  @Test
  void stopsAModelThatReachesItsSpendLimit() {
    env.answers = (c, run) -> result(c.id(), run, true, 400_000);

    int code =
        run(
            "--models",
            "a",
            "--runs",
            "3",
            "--budget-usd",
            "1.00",
            "--price-in",
            "a=1",
            "--price-out",
            "a=2");

    assertThat(code).isZero();
    assertThat(out()).contains("spend limit reached").contains("(stopped: spend limit)");
  }

  @Test
  void aFailedRunIsReportedAndStillExitsWithZero() {
    env.answers = (c, run) -> result(c.id(), run, false, 0);

    assertThat(
            run(
                "--models",
                "a",
                "--runs",
                "1",
                "--cases",
                "plain-week",
                "--price-in",
                "a=1",
                "--price-out",
                "a=2"))
        .isZero();
    assertThat(out()).contains("FAIL").contains("bad id 999");
  }

  @Test
  void aHarnessThatCannotStartIsReportedByClassOnly() {
    env.openFails = new IllegalStateException("secret prompt text");

    assertThat(run("--models", "a", "--price-in", "a=1", "--price-out", "a=2")).isEqualTo(1);
    assertThat(err()).contains("IllegalStateException").doesNotContain("secret prompt text");
  }

  @Test
  void anUnwritableReportFolderExitsWithOne() throws IOException {
    Path file = Files.writeString(reports.resolve("blocker"), "x");
    env.reportDir = file;

    assertThat(
            run(
                "--models",
                "a",
                "--runs",
                "1",
                "--cases",
                "plain-week",
                "--price-in",
                "a=1",
                "--price-out",
                "a=2"))
        .isEqualTo(1);
    assertThat(err()).contains("Cannot write the report");
  }

  @Test
  void selectKeepsTheFileOrderAndAddsBaselines() {
    EvalCase.Suite suite = EvalCase.load();

    assertThat(EvalRunner.select(suite, List.of()).size()).isEqualTo(suite.cases().size());
    assertThat(
            EvalRunner.select(suite, List.of("less-meat", "chat-in-scope")).stream()
                .map(EvalCase::id))
        .containsExactly("plain-week", "less-meat", "chat-in-scope");
    assertThatThrownBy(() -> EvalRunner.select(suite, List.of("zzz")))
        .isInstanceOf(UsageException.class);
  }
}
