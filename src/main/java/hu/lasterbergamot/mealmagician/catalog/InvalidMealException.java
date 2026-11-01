package hu.lasterbergamot.mealmagician.catalog;

/**
 * The submitted meal or tag broke an input rule. A page catches it and shows the form again with a
 * message chosen from {@link #problem()}; it is not an HTTP error by itself.
 */
public class InvalidMealException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final MealProblem problem;

  /**
   * Creates the exception.
   *
   * @param problem the rule that was broken
   */
  public InvalidMealException(MealProblem problem) {
    super("Meal input refused: " + problem);
    this.problem = problem;
  }

  /**
   * The rule that was broken.
   *
   * @return the problem, which tells the page which message to show
   */
  public MealProblem problem() {
    return problem;
  }
}
