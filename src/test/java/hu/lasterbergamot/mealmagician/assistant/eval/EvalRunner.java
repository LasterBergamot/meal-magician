package hu.lasterbergamot.mealmagician.assistant.eval;

import hu.lasterbergamot.mealmagician.assistant.eval.EvalOptions.UsageException;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The command line of the assistant eval ({@code task ai:eval}): runs every case several times
 * against each model that is named, applies the deterministic checks, prints a table and writes
 * {@code build/eval/<timestamp>.md}.
 *
 * <p>It is a plain {@code main}, not a test, and it is never part of {@code task test} or CI: it
 * calls the real Gemini model on Vertex AI with the owner's own login, which costs money and gives
 * a slightly different answer every time. The owner reads the report and signs off the model
 * (PLAN.md D33).
 *
 * <p>Exit codes: 0 when the eval ran (some checks failing is a finding, not an error) or for {@code
 * --help}; 2 when the command line is wrong (for example no {@code --models}); 1 when the machine
 * is not ready (no Docker, no Google login) or the run broke.
 */
public final class EvalRunner {

  private static final String USAGE =
      """
      Usage: task ai:eval -- --models <id,id> [options]

      Runs the assistant eval: every case against every model, several times, with deterministic
      checks. It calls the REAL model and costs money, so it never runs in CI or in `task test`.
      The owner reads the report (build/eval/<timestamp>.md) and signs off the model (D33).

      Options:
        --models <id,id>       chat model ids to compare, for example gemini-3.5-flash (required)
        --runs <n>             runs per case and model, to see the variance (default 3)
        --cases <id,id>        only these cases (default all; a baseline case is added when needed)
        --price-in <m=usd,..>  USD per million input tokens, per model (else env AI_PRICE_IN)
        --price-out <m=usd,..> USD per million output tokens, per model (else env AI_PRICE_OUT)
        --budget-usd <usd>     stop a model once its runs add up to this much (default 2.00)
        --help                 this text (needs no credentials and no Docker)

      Needs: Docker (a throw-away Postgres with pgvector runs in Testcontainers), and the owner's
      Google login: `gcloud auth application-default login`, plus the environment variables
      AI_PROJECT_ID, AI_LOCATION, AI_EMBEDDING_MODEL (and AI_EMBEDDING_LOCATION if it differs).
      The data is an invented household (src/test/resources/eval); the real database is never used.
      """;

  private EvalRunner() {}

  /**
   * Runs the eval and exits the JVM with its exit code.
   *
   * @param args the command line, see {@code --help}
   */
  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err, new SpringEnvironment()));
  }

  /**
   * Runs the eval.
   *
   * @param args the command line
   * @param out where the usage text, the progress and the report go
   * @param err where problems go
   * @param env the machine
   * @return the exit code: 0, 1 or 2 (see the class comment)
   */
  static int run(String[] args, PrintStream out, PrintStream err, EvalEnvironment env) {
    EvalOptions options;
    try {
      options = EvalOptions.parse(args);
    } catch (UsageException e) {
      err.println("Error: " + e.getMessage());
      err.println();
      err.print(USAGE);
      return 2;
    }
    if (options.help()) {
      out.print(USAGE);
      return 0;
    }
    EvalCase.Suite suite;
    List<EvalCase> selected;
    try {
      suite = EvalCase.load();
      selected = select(suite, options.cases());
    } catch (UsageException e) {
      err.println("Error: " + e.getMessage());
      return 2;
    } catch (IllegalStateException e) {
      err.println("Error: " + e.getMessage());
      return 1;
    }
    List<Prices> prices = new ArrayList<>();
    for (String model : options.models()) {
      Optional<Prices> price = prices(model, options, env);
      if (price.isEmpty()) {
        err.println(
            "Error: no price for "
                + model
                + ". Give --price-in "
                + model
                + "=<usd per million> and --price-out "
                + model
                + "=<usd per million>, or set AI_PRICE_IN and AI_PRICE_OUT.");
        return 2;
      }
      prices.add(price.get());
    }
    if (!env.dockerAvailable()) {
      err.println(
          "Docker is not available. The eval starts a throw-away Postgres with pgvector through"
              + " Testcontainers; start Docker and try again.");
      return 1;
    }
    Optional<String> problem = env.googleProblem();
    if (problem.isPresent()) {
      err.println("Cannot reach Google: " + problem.get());
      return 1;
    }
    return evaluate(options, suite, selected, prices, out, err, env);
  }

  private static int evaluate(
      EvalOptions options,
      EvalCase.Suite suite,
      List<EvalCase> selected,
      List<Prices> prices,
      PrintStream out,
      PrintStream err,
      EvalEnvironment env) {
    Instant started = env.now();
    List<RunResult> results = new ArrayList<>();
    Set<String> stopped = new LinkedHashSet<>();
    long budgetMicro = options.budgetUsd().multiply(BigDecimal.valueOf(1_000_000L)).longValue();
    for (Prices price : prices) {
      out.println(
          "Model "
              + price.model()
              + ": "
              + options.runs()
              + " runs of "
              + selected.size()
              + " cases");
      long spent = 0;
      try (EvalEnvironment.Harness harness = env.open(price.model(), price.in(), price.out())) {
        runs:
        for (int run = 1; run <= options.runs(); run++) {
          for (EvalCase c : selected) {
            if (spent >= budgetMicro) {
              stopped.add(price.model());
              out.println("  spend limit reached, stopping " + price.model());
              break runs;
            }
            RunResult result = harness.run(c, run);
            results.add(result);
            spent += result.costMicroUsd();
            out.printf(
                Locale.ROOT,
                "  run %d %-18s %s  %d ms%n",
                run,
                c.id(),
                result.passed() ? "pass" : "FAIL",
                result.latencyMillis());
          }
        }
      } catch (RuntimeException e) {
        // The class only: a provider's message can quote the prompt.
        err.println("The eval stopped for " + price.model() + ": " + e.getClass().getSimpleName());
        return 1;
      }
    }
    Report.Meta meta =
        new Report.Meta(
            started,
            options.models(),
            options.runs(),
            selected.stream().map(EvalCase::id).toList(),
            options.budgetUsd(),
            stopped);
    String report = Report.render(results, meta);
    out.println();
    out.print(report);
    try {
      Path file = Report.write(env.reportDir(), started, report);
      out.println("Report written to " + file);
    } catch (IOException e) {
      err.println("Cannot write the report: " + e.getMessage());
      return 1;
    }
    return 0;
  }

  /**
   * Picks the cases to run: all of them, or the named ones plus any baseline they need, in the
   * order of the case file (so a baseline always runs before the case that compares with it).
   *
   * @param suite the case file
   * @param names the case ids from {@code --cases}; empty for all
   * @return the cases to run
   * @throws UsageException if a name is not a case
   */
  static List<EvalCase> select(EvalCase.Suite suite, List<String> names) {
    if (names.isEmpty()) {
      return suite.cases();
    }
    Set<String> wanted = new LinkedHashSet<>(names);
    for (String name : names) {
      EvalCase c;
      try {
        c = suite.get(name);
      } catch (IllegalArgumentException e) {
        throw new UsageException(
            "no case named "
                + name
                + "; the cases are "
                + suite.cases().stream().map(EvalCase::id).toList());
      }
      if (c.baseline() != null) {
        wanted.add(c.baseline());
      }
    }
    return suite.cases().stream().filter(c -> wanted.contains(c.id())).toList();
  }

  private record Prices(String model, BigDecimal in, BigDecimal out) {}

  private static Optional<Prices> prices(String model, EvalOptions options, EvalEnvironment env) {
    BigDecimal in = options.priceIn().getOrDefault(model, fromEnv(env, "AI_PRICE_IN"));
    BigDecimal out = options.priceOut().getOrDefault(model, fromEnv(env, "AI_PRICE_OUT"));
    return in == null || out == null ? Optional.empty() : Optional.of(new Prices(model, in, out));
  }

  private static BigDecimal fromEnv(EvalEnvironment env, String name) {
    String value = env.get(name);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.strip());
    } catch (NumberFormatException notANumber) {
      return null;
    }
  }
}
