package hu.lasterbergamot.mealmagician.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The harness end to end on the real services and a real Postgres, with the scripted model of the
 * {@code fake-ai} profile in place of Gemini: no Google account, no network, no cost. It proves
 * that the fixture household loads, every case can run, usage is read from the ledger and the
 * report is written. Whether the scripted answers pass the checks is not the point (they were not
 * written for them); that the checks run on real drafts is.
 */
@Tag("integration")
class EvalHarnessTest {

  @TempDir Path reports;

  @Test
  void runsEveryCaseThroughTheRealServicesAndWritesAReport() throws IOException {
    List<EvalEnvironment.Harness> opened = new ArrayList<>();
    EvalEnvironment env =
        new EvalEnvironment() {
          @Override
          public String get(String name) {
            return null;
          }

          @Override
          public boolean dockerAvailable() {
            return true;
          }

          @Override
          public Optional<String> googleProblem() {
            return Optional.empty();
          }

          @Override
          public Harness open(String model, BigDecimal priceInUsd, BigDecimal priceOutUsd) {
            Harness harness = EvalHarness.start(model, priceInUsd, priceOutUsd, true);
            opened.add(harness);
            return harness;
          }

          @Override
          public Path reportDir() {
            return reports;
          }

          @Override
          public Instant now() {
            return Instant.parse("2026-11-04T10:15:30Z");
          }
        };
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    int code =
        EvalRunner.run(
            new String[] {
              "--models",
              "scripted",
              "--runs",
              "1",
              "--price-in",
              "scripted=1.00",
              "--price-out",
              "scripted=2.00"
            },
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8),
            env);

    assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(code).isZero();
    String report = Files.readString(reports.resolve("20261104-101530.md"));
    assertThat(report)
        .contains("| scripted | 16 |")
        .contains("| plain-week | ")
        .contains("| chat-off-topic | ");
    // Every case ran and none of them broke: failures would be listed as "no answer".
    assertThat(report).doesNotContain("no answer");
    // The scripted model's calls were metered through the ledger.
    assertThat(report).doesNotContain("| scripted | 16 | 0% | 0/0 |");
  }

  @Test
  void aCaseReportsItsUsageFromTheLedgerAndItsChecks() {
    try (EvalHarness harness =
        EvalHarness.start("scripted", BigDecimal.ONE, BigDecimal.TEN, true)) {
      EvalCase.Suite suite = EvalCase.load();

      RunResult plain = harness.run(suite.get("plain-week"), 1);
      RunResult lessMeat = harness.run(suite.get("less-meat"), 1);
      RunResult chat = harness.run(suite.get("chat-off-topic"), 1);

      assertThat(plain.error()).isNull();
      assertThat(plain.inputTokens()).isPositive();
      assertThat(plain.costMicroUsd()).isPositive();
      assertThat(plain.checks())
          .extracting(Checks.Named::check)
          .contains("realIds", "oneWeek", "language");
      assertThat(
              plain.checks().stream()
                  .filter(c -> c.check().equals("realIds"))
                  .findFirst()
                  .orElseThrow()
                  .result()
                  .pass())
          .isTrue();
      assertThat(lessMeat.checks()).extracting(Checks.Named::check).contains("lessMeat");
      assertThat(
              lessMeat.checks().stream()
                  .filter(c -> c.check().equals("lessMeat"))
                  .findFirst()
                  .orElseThrow()
                  .result()
                  .message())
          .doesNotContain("baseline case");
      assertThat(chat.error()).isNull();
    }
  }
}
