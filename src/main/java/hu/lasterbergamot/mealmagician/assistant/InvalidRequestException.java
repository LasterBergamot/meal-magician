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

  /** The longest chat message the wizard accepts, in characters (#161). */
  public static final int CHAT_MAX_LENGTH = 1000;

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a draft request, which is limited to {@link #MAX_LENGTH}. The message
   * holds only the lengths, never the text.
   *
   * @param length how long the refused text was
   */
  public InvalidRequestException(int length) {
    this(length, MAX_LENGTH);
  }

  /**
   * Creates the exception for a text that is longer than its limit. The message holds only the
   * lengths, never the text, so it can be logged safely.
   *
   * @param length how long the refused text was
   * @param limit the longest text the feature accepts
   */
  public InvalidRequestException(int length, int limit) {
    super("The request is " + length + " characters; the limit is " + limit);
  }
}
