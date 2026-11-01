package hu.lasterbergamot.mealmagician.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The owner addressed an invite that does not exist <em>or</em> belongs to another household. Like
 * {@link HouseholdNotFoundException} it is one exception for both cases, so another household's
 * invite ids cannot be probed. It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InviteNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param inviteId the id that was asked for
   */
  public InviteNotFoundException(long inviteId) {
    super("No invite " + inviteId + " in this household");
  }
}
