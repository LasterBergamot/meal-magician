package hu.lasterbergamot.mealmagician.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Someone else changed the slot after the caller read it, so the change would overwrite their edit.
 * It is raised in two places: when the version the caller sent is already old (checked before any
 * write), and when the database refuses the write because the row changed in the meantime. The page
 * shows "someone else changed this, reload" and answers with HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PlanConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param slotId the slot whose version was stale
   */
  public PlanConflictException(long slotId) {
    super("Planned slot " + slotId + " was changed by someone else");
  }
}
