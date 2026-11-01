package hu.lasterbergamot.mealmagician.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * An owner addressed a member who is not in this household, for example because they left a moment
 * ago, or because the id belongs to someone in another household. One exception for both cases, so
 * other households' members cannot be probed. It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MemberNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param userId the {@code app_user} id that was asked for
   */
  public MemberNotFoundException(long userId) {
    super("No member " + userId + " in this household");
  }
}
