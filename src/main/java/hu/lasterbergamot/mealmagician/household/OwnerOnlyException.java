package hu.lasterbergamot.mealmagician.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A plain member tried an action only owners may do, such as renaming the household. It answers
 * with HTTP 403: the person is a member, so the household's existence is no secret to them.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class OwnerOnlyException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param householdId the household the action was aimed at
   */
  public OwnerOnlyException(long householdId) {
    super("Only an owner of household " + householdId + " may do this");
  }
}
