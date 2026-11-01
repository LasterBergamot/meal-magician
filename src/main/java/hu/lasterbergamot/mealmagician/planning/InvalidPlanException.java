package hu.lasterbergamot.mealmagician.planning;

/**
 * The submitted slot input broke an input rule. A page catches it and shows the form again with a
 * message chosen from {@link #problem()}; it is not an HTTP error by itself. Nothing is written
 * when it is thrown.
 */
public class InvalidPlanException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final PlanProblem problem;

  /**
   * Creates the exception.
   *
   * @param problem the rule that was broken
   */
  public InvalidPlanException(PlanProblem problem) {
    super("Plan input refused: " + problem);
    this.problem = problem;
  }

  /**
   * The rule that was broken.
   *
   * @return the problem, which tells the page which message to show
   */
  public PlanProblem problem() {
    return problem;
  }
}
