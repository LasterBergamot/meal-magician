package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The draft was already accepted, so it can no longer be refined, trimmed or accepted again. This
 * is what makes accepting idempotent: a second accept (a double click, or two members at once) is
 * refused and writes nothing. Left uncaught it answers with HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DraftClosedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final long draftId;

  /**
   * Creates the exception.
   *
   * @param draftId the draft that is no longer open
   */
  public DraftClosedException(long draftId) {
    super("Draft " + draftId + " is no longer open");
    this.draftId = draftId;
  }

  /**
   * The draft that is no longer open, so a page can find the week it belongs to.
   *
   * @return the draft's id
   */
  public long draftId() {
    return draftId;
  }
}
