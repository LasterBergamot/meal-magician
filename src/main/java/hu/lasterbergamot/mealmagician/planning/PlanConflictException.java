package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;
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

  /**
   * Creates the exception for a replace that lost a race: the slots of the target range changed
   * while they were being deleted, so nothing was written.
   *
   * @param first the first day of the replaced range
   * @param last the last day of the replaced range
   */
  public PlanConflictException(LocalDate first, LocalDate last) {
    super("The plan from " + first + " to " + last + " was changed by someone else");
  }
}
