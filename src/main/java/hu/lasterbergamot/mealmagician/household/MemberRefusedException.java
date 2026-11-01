package hu.lasterbergamot.mealmagician.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A membership change broke a household rule, such as the last owner leaving. The controller
 * catches it to show {@link MemberProblem#messageKey()} on the members page; anywhere else it
 * answers with HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class MemberRefusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final MemberProblem problem;

  /**
   * Creates the exception.
   *
   * @param problem the rule that was broken
   */
  public MemberRefusedException(MemberProblem problem) {
    super("Membership change refused: " + problem);
    this.problem = problem;
  }

  /**
   * The rule that was broken.
   *
   * @return the problem, which names the message to show
   */
  public MemberProblem problem() {
    return problem;
  }
}
