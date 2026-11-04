package hu.lasterbergamot.mealmagician.assistant.eval;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.testcontainers.DockerClientFactory;

/**
 * The real machine for {@link EvalRunner}: the process environment, a Docker check, the owner's
 * Google login and the real {@link EvalHarness}.
 */
final class SpringEnvironment implements EvalEnvironment {

  /** The variables the application reads to reach Vertex AI (see Hosting-Setup, Part 9). */
  private static final List<String> REQUIRED =
      List.of("AI_PROJECT_ID", "AI_LOCATION", "AI_EMBEDDING_MODEL");

  @Override
  public String get(String name) {
    return System.getenv(name);
  }

  @Override
  public boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError noDocker) {
      return false;
    }
  }

  @Override
  public Optional<String> googleProblem() {
    List<String> missing = new ArrayList<>();
    for (String name : REQUIRED) {
      String value = get(name);
      if (value == null || value.isBlank()) {
        missing.add(name);
      }
    }
    if (!missing.isEmpty()) {
      return Optional.of(
          "set " + String.join(", ", missing) + " (the same values as the app's settings).");
    }
    try {
      GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      return Optional.of("no Google login found. Run `gcloud auth application-default login`.");
    }
    return Optional.empty();
  }

  @Override
  public Harness open(String model, BigDecimal priceInUsd, BigDecimal priceOutUsd) {
    return EvalHarness.start(model, priceInUsd, priceOutUsd, false);
  }

  @Override
  public Path reportDir() {
    return Path.of("build", "eval");
  }

  @Override
  public Instant now() {
    return Instant.now();
  }
}
