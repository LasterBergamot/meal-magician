package hu.lasterbergamot.mealmagician.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportTest {

  private static final Instant STARTED = Instant.parse("2026-11-04T10:15:30Z");

  private static RunResult run(String model, String caseId, int run, boolean pass, long latency) {
    return new RunResult(
        model,
        caseId,
        run,
        List.of(
            new Checks.Named("realIds", Checks.Result.ok("fine")),
            new Checks.Named(
                "language", pass ? Checks.Result.ok("hu") : Checks.Result.fail("looks like en"))),
        null,
        1_000,
        400,
        5_500,
        latency,
        false);
  }

  private static Report.Meta meta(Set<String> stopped) {
    return new Report.Meta(
        STARTED,
        List.of("flash", "lite"),
        2,
        List.of("plain-week", "less-meat"),
        new BigDecimal("2.00"),
        stopped);
  }

  @Test
  void theSummaryHasOneRowPerModelWithPassRateTokensCostAndLatency() {
    List<RunResult> results =
        List.of(
            run("flash", "plain-week", 1, true, 2_000),
            run("flash", "plain-week", 2, false, 4_000),
            new RunResult(
                "lite",
                "plain-week",
                1,
                List.of(),
                "ModelUnavailableException",
                0,
                0,
                0,
                500,
                true));

    String report = Report.render(results, meta(Set.of()));

    assertThat(report)
        .contains(
            "| Model | Runs | Pass rate | Checks passed | Invalid-id rate | Tokens in | Tokens out | Cost (USD) | Cost per case (USD) | Latency avg (s) | Latency p95 (s) |")
        .contains("| flash | 2 | 50% | 3/4 | 0% | 2000 | 800 | 0.0110 | 0.0055 | 3.0 | 4.0 |")
        .contains("| lite | 1 | 0% | 0/0 | 100% | 0 | 0 | 0.0000 | 0.0000 | 0.5 | 0.5 |");
  }

  @Test
  void aModelWithoutRunsShowsDashes() {
    String report = Report.render(List.of(), meta(Set.of()));

    assertThat(report).contains("| flash | 0 | - | 0/0 | - | 0 | 0 | 0.0000 | 0.0000 | - | - |");
  }

  @Test
  void theCaseTableCountsPassedRunsPerModel() {
    List<RunResult> results =
        List.of(
            run("flash", "plain-week", 1, true, 1),
            run("flash", "plain-week", 2, false, 1),
            run("lite", "plain-week", 1, true, 1));

    String report = Report.render(results, meta(Set.of()));

    assertThat(report)
        .contains("| Case | flash | lite |")
        .contains("| plain-week | 1/2 | 1/1 |")
        .contains("| less-meat | 0/0 | 0/0 |");
  }

  @Test
  void failuresNameTheCaseTheRunTheCheckAndTheMessage() {
    List<RunResult> results =
        List.of(
            run("flash", "plain-week", 2, false, 1),
            new RunResult(
                "lite",
                "less-meat",
                1,
                List.of(),
                "DraftGenerationException: bad",
                0,
                0,
                0,
                1,
                false));

    String report = Report.render(results, meta(Set.of()));

    assertThat(report)
        .contains("### flash")
        .contains("- `plain-week` run 2: language: looks like en")
        .contains("### lite")
        .contains("- `less-meat` run 1: no answer (DraftGenerationException: bad)");
  }

  @Test
  void aReportWithoutFailuresSaysSoAndAStoppedModelIsMarked() {
    String report =
        Report.render(List.of(run("flash", "plain-week", 1, true, 1)), meta(Set.of("lite")));

    assertThat(report).contains("No run failed.").contains("| lite (stopped: spend limit) |");
  }

  @Test
  void theReportSaysTheOwnerSignsOffAndHoldsNoDecision() {
    String report = Report.render(List.of(), meta(Set.of()));

    assertThat(report)
        .contains("The owner reviews this report and signs off one model")
        .contains("Spend limit per model: $2.00");
  }

  @Test
  void writesTheReportToATimestampedFile(@TempDir Path dir) throws IOException {
    Path file = Report.write(dir.resolve("eval"), STARTED, "text");

    assertThat(file.getFileName().toString()).isEqualTo("20261104-101530.md");
    assertThat(Files.readString(file)).isEqualTo("text");
  }

  @Test
  void aRunPassesOnlyWithAnAnswerAndEveryCheckGreen() {
    assertThat(run("m", "c", 1, true, 1).passed()).isTrue();
    assertThat(run("m", "c", 1, false, 1).passed()).isFalse();
    assertThat(new RunResult("m", "c", 1, List.of(), "x", 0, 0, 0, 0, false).passed()).isFalse();
  }
}
