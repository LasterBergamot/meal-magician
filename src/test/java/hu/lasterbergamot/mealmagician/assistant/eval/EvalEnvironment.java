package hu.lasterbergamot.mealmagician.assistant.eval;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Everything the runner needs from the machine it runs on, behind one interface so the runner's
 * argument handling and loop can be tested with no Docker, no Google account and no model.
 */
interface EvalEnvironment {

  /**
   * Reads an environment variable.
   *
   * @param name the variable
   * @return its value, or {@code null} if unset
   */
  String get(String name);

  /**
   * Whether a Docker daemon is reachable, which Testcontainers needs for the throw-away Postgres.
   *
   * @return {@code true} if it is
   */
  boolean dockerAvailable();

  /**
   * Whether the Google connection can work: the project, the locations and the embedding model are
   * set and the owner's Application Default Credentials exist.
   *
   * @return what is missing, in a sentence a person can act on, or empty if all is in place
   */
  Optional<String> googleProblem();

  /**
   * Starts the assistant for one model: a Spring context on the throw-away Postgres, with the
   * fixture household loaded through the real services.
   *
   * @param model the chat model id
   * @param priceInUsd USD per million input tokens
   * @param priceOutUsd USD per million output tokens
   * @return the running harness; close it to stop the context
   */
  Harness open(String model, BigDecimal priceInUsd, BigDecimal priceOutUsd);

  /**
   * The folder the report goes to.
   *
   * @return normally {@code build/eval}
   */
  Path reportDir();

  /**
   * The current time.
   *
   * @return now
   */
  Instant now();

  /** A running assistant for one model, able to run cases against it. */
  interface Harness extends AutoCloseable {

    /**
     * Runs one case once and checks the answer. A failure of the assistant is a result with an
     * error, not an exception.
     *
     * @param c the case
     * @param run the run number, from 1
     * @return the result
     */
    RunResult run(EvalCase c, int run);

    /** Stops the Spring context. */
    @Override
    void close();
  }
}
