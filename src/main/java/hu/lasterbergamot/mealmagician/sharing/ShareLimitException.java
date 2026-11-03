package hu.lasterbergamot.mealmagician.sharing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The household already has the most active share links it may have. A page may catch it to show a
 * message; left uncaught it answers with HTTP 422. Nothing is written when it is thrown. Revoking a
 * link, or waiting for one to expire, makes room again.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class ShareLimitException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param limit the number of active links a household may have
   */
  public ShareLimitException(int limit) {
    super("A household may have at most " + limit + " active share links");
  }
}
