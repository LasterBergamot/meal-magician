package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A request or follow-up message is longer than {@link #MAX_LENGTH} characters. It is thrown before
 * any model call, so nothing was sent and nothing was charged. The controller normally checks the
 * member's own text (at most 1 000 characters) first; this is the service's backstop for the text
 * plus the quick-request phrases the controller appends. Left uncaught it answers with HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {

  /** The longest request or message the assistant accepts, in characters. */
  public static final int MAX_LENGTH = 1200;

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception. The message holds only the length, never the text.
   *
   * @param length how long the refused text was
   */
  public InvalidRequestException(int length) {
    super("The request is " + length + " characters; the limit is " + MAX_LENGTH);
  }
}
