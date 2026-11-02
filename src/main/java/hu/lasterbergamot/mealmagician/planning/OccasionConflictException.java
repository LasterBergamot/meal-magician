package hu.lasterbergamot.mealmagician.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Someone else changed the occasion after the caller read it, so an edit or delete would overwrite
 * their change. It is raised when the version the caller sent is already old (checked before any
 * write) and when the database refuses the write because the row changed in the meantime. It
 * answers with HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class OccasionConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param occasionId the occasion whose version was stale
   */
  public OccasionConflictException(long occasionId) {
    super("Occasion " + occasionId + " was changed by someone else");
  }
}
