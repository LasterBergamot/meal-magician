package hu.lasterbergamot.mealmagician.household;

import lombok.Getter;

/**
 * A join link cannot be used. The controller turns it into a page that explains the reason, so it
 * is not an HTTP error by itself. The message never contains the code.
 */
@Getter
public class InviteRefusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Why the link was refused. */
  private final InviteProblem problem;

  /**
   * Creates the exception.
   *
   * @param problem why the link was refused
   */
  public InviteRefusedException(InviteProblem problem) {
    super("Invite refused: " + problem);
    this.problem = problem;
  }
}
