package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The draft, or the item asked for, does not exist in this household. A draft of another household
 * gives the same answer as one that never existed, so ids cannot be probed. Left uncaught it
 * answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DraftNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception for a missing draft.
   *
   * @param draftId the id that was asked for
   */
  public DraftNotFoundException(long draftId) {
    super("No draft " + draftId + " in this household");
  }

  /**
   * Creates the exception for a missing item, or an item that is not an unresolved idea.
   *
   * @param draftId the draft
   * @param itemId the item that was asked for
   */
  public DraftNotFoundException(long draftId, long itemId) {
    super("No such item " + itemId + " in draft " + draftId);
  }
}
