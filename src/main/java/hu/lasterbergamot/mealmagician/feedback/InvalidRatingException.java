package hu.lasterbergamot.mealmagician.feedback;

/**
 * The submitted rating broke a rule. A page catches it and shows the form again with a message
 * chosen from {@link #problem()} (the rating page answers 422, #128). Nothing is written when it is
 * thrown.
 */
public class InvalidRatingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final RatingProblem problem;

  /**
   * Creates the exception.
   *
   * @param problem the rule that was broken
   */
  public InvalidRatingException(RatingProblem problem) {
    super("Rating refused: " + problem);
    this.problem = problem;
  }

  /**
   * The rule that was broken.
   *
   * @return the problem, which tells the page which message to show
   */
  public RatingProblem problem() {
    return problem;
  }
}
