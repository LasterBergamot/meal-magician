package hu.lasterbergamot.mealmagician.assistant.eval;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/** A machine for the runner's tests: no Docker, no Google, and a harness that is a function. */
final class FakeEnvironment implements EvalEnvironment {

  boolean docker = true;
  String googleProblem;
  Map<String, String> variables = Map.of();
  Path reportDir;
  RuntimeException openFails;
  BiFunction<EvalCase, Integer, RunResult> answers;
  final List<String> opened = new ArrayList<>();
  final List<BigDecimal> pricesIn = new ArrayList<>();

  @Override
  public String get(String name) {
    return variables.get(name);
  }

  @Override
  public boolean dockerAvailable() {
    return docker;
  }

  @Override
  public Optional<String> googleProblem() {
    return Optional.ofNullable(googleProblem);
  }

  @Override
  public Harness open(String model, BigDecimal priceInUsd, BigDecimal priceOutUsd) {
    if (openFails != null) {
      throw openFails;
    }
    opened.add(model);
    pricesIn.add(priceInUsd);
    return new Harness() {
      @Override
      public RunResult run(EvalCase c, int run) {
        RunResult made = answers.apply(c, run);
        return new RunResult(
            model,
            made.caseId(),
            made.run(),
            made.checks(),
            made.error(),
            made.inputTokens(),
            made.outputTokens(),
            made.costMicroUsd(),
            made.latencyMillis(),
            made.invalidIds());
      }

      @Override
      public void close() {}
    };
  }

  @Override
  public Path reportDir() {
    return reportDir;
  }

  @Override
  public Instant now() {
    return Instant.parse("2026-11-04T10:15:30Z");
  }
}
