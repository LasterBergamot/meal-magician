package hu.lasterbergamot.mealmagician.identity.internal;

/**
 * An allowlist change broke a rule. The controller catches it to show the reason on the admin page,
 * so it never reaches the error handler.
 */
class AllowlistRefusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final AllowlistProblem problem;

  AllowlistRefusedException(AllowlistProblem problem) {
    super("Allowlist change refused: " + problem);
    this.problem = problem;
  }

  /**
   * The rule that was broken.
   *
   * @return the problem, which names the message to show
   */
  AllowlistProblem problem() {
    return problem;
  }
}
