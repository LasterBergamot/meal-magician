package hu.lasterbergamot.mealmagician.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The occasion does not exist <em>or</em> it belongs to another household. One exception for both,
 * so a stranger cannot tell which occasion ids exist (PLAN.md D8). It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OccasionNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param occasionId the id that was asked for; kept out of the error page but useful in logs
   */
  public OccasionNotFoundException(long occasionId) {
    super("No occasion " + occasionId + " in this household");
  }
}
