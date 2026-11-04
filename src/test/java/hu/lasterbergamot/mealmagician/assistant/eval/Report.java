package hu.lasterbergamot.mealmagician.assistant.eval;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the results of an eval into the Markdown report the owner reads: one summary row per model
 * (pass rate, invalid-id rate, tokens, estimated cost, latency), a pass rate per case, and the
 * failures with the message of each failed check.
 *
 * <p>The report holds model ids, counts and check messages about invented meals. It never holds a
 * project id, a credential or a model's whole answer.
 */
public final class Report {

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
  private static final double MICRO = 1_000_000.0;

  private Report() {}

  /**
   * What the report says about the run as a whole.
   *
   * @param started when the eval started
   * @param models the models, in the order they ran
   * @param runsPerCase how many times each case was run per model
   * @param caseIds the cases that ran, in order
   * @param budgetUsd the spend limit per model
   * @param stoppedByBudget the models that were stopped because they reached the limit
   */
  public record Meta(
      Instant started,
      List<String> models,
      int runsPerCase,
      List<String> caseIds,
      BigDecimal budgetUsd,
      Set<String> stoppedByBudget) {}

  /**
   * Builds the report text.
   *
   * @param results every run, in any order
   * @param meta what the report says about the whole run
   * @return the Markdown text
   */
  public static String render(List<RunResult> results, Meta meta) {
    StringBuilder out = new StringBuilder();
    out.append("# Assistant eval report\n\n");
    out.append("- Started: ").append(meta.started()).append('\n');
    out.append("- Models: ").append(String.join(", ", meta.models())).append('\n');
    out.append("- Runs per case: ").append(meta.runsPerCase()).append('\n');
    out.append("- Cases: ").append(String.join(", ", meta.caseIds())).append('\n');
    out.append("- Spend limit per model: $").append(meta.budgetUsd().toPlainString()).append('\n');
    out.append(
        "- Status: raw results of deterministic checks. The owner reviews this report and signs"
            + " off one model (PLAN.md D33); nothing here is a decision.\n\n");
    summary(out, results, meta);
    perCase(out, results, meta);
    failures(out, results, meta);
    return out.toString();
  }

  private static void summary(StringBuilder out, List<RunResult> results, Meta meta) {
    out.append("## Summary\n\n");
    out.append(
        "| Model | Runs | Pass rate | Checks passed | Invalid-id rate | Tokens in | Tokens out"
            + " | Cost (USD) | Cost per case (USD) | Latency avg (s) | Latency p95 (s) |\n");
    out.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
    for (String model : meta.models()) {
      List<RunResult> mine = results.stream().filter(r -> r.model().equals(model)).toList();
      int runs = mine.size();
      long passed = mine.stream().filter(RunResult::passed).count();
      long checks = mine.stream().mapToLong(r -> r.checks().size()).sum();
      long checksPassed =
          mine.stream().flatMap(r -> r.checks().stream()).filter(c -> c.result().pass()).count();
      long invalid = mine.stream().filter(RunResult::invalidIds).count();
      long in = mine.stream().mapToLong(RunResult::inputTokens).sum();
      long outTokens = mine.stream().mapToLong(RunResult::outputTokens).sum();
      long cost = mine.stream().mapToLong(RunResult::costMicroUsd).sum();
      List<Long> latencies = mine.stream().map(RunResult::latencyMillis).sorted().toList();
      out.append(
          String.format(
              Locale.ROOT,
              "| %s%s | %d | %s | %d/%d | %s | %d | %d | %s | %s | %s | %s |%n",
              model,
              meta.stoppedByBudget().contains(model) ? " (stopped: spend limit)" : "",
              runs,
              percent(passed, runs),
              checksPassed,
              checks,
              percent(invalid, runs),
              in,
              outTokens,
              usd(cost),
              usd(runs == 0 ? 0 : Math.round((double) cost / runs)),
              latencies.isEmpty()
                  ? "-"
                  : String.format(
                      Locale.ROOT,
                      "%.1f",
                      latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000),
              latencies.isEmpty() ? "-" : seconds(percentile95(latencies))));
    }
    out.append('\n');
  }

  private static void perCase(StringBuilder out, List<RunResult> results, Meta meta) {
    out.append("## Passed runs per case\n\n");
    out.append("| Case | ").append(String.join(" | ", meta.models())).append(" |\n");
    out.append("|---|").append("---|".repeat(meta.models().size())).append('\n');
    for (String caseId : meta.caseIds()) {
      out.append("| ").append(caseId);
      for (String model : meta.models()) {
        List<RunResult> mine =
            results.stream()
                .filter(r -> r.model().equals(model) && r.caseId().equals(caseId))
                .toList();
        out.append(" | ").append(mine.stream().filter(RunResult::passed).count());
        out.append('/').append(mine.size());
      }
      out.append(" |\n");
    }
    out.append('\n');
  }

  private static void failures(StringBuilder out, List<RunResult> results, Meta meta) {
    out.append("## Failures\n\n");
    boolean any = false;
    for (String model : meta.models()) {
      List<String> lines = new ArrayList<>();
      for (RunResult r : results) {
        if (!r.model().equals(model) || r.passed()) {
          continue;
        }
        if (r.error() != null) {
          lines.add("- `" + r.caseId() + "` run " + r.run() + ": no answer (" + r.error() + ")");
        }
        r.checks().stream()
            .filter(c -> !c.result().pass())
            .forEach(c ->
                lines.add(
                    "- `"
                        + r.caseId()
                        + "` run "
                        + r.run()
                        + ": "
                        + c.check()
                        + ": "
                        + c.result().message()));
      }
      if (!lines.isEmpty()) {
        any = true;
        out.append("### ").append(model).append("\n\n");
        out.append(String.join("\n", lines)).append("\n\n");
      }
    }
    if (!any) {
      out.append("No run failed.\n");
    }
  }

  /**
   * Writes a report to {@code <dir>/<timestamp>.md}, creating the folder if needed.
   *
   * @param dir the folder, normally {@code build/eval}
   * @param started the start time, which names the file (UTC, {@code yyyyMMdd-HHmmss})
   * @param content the report text
   * @return the path written
   * @throws IOException if the file cannot be written
   */
  public static Path write(Path dir, Instant started, String content) throws IOException {
    Files.createDirectories(dir);
    Path file = dir.resolve(STAMP.format(started) + ".md");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private static String percent(long part, long whole) {
    return whole == 0 ? "-" : String.format(Locale.ROOT, "%.0f%%", 100.0 * part / whole);
  }

  private static String usd(long micro) {
    return String.format(Locale.ROOT, "%.4f", micro / MICRO);
  }

  private static String seconds(long millis) {
    return String.format(Locale.ROOT, "%.1f", millis / 1000.0);
  }

  private static long percentile95(List<Long> sorted) {
    int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
    return sorted.get(Math.max(index, 0));
  }
}
