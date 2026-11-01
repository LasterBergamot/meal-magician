package hu.lasterbergamot.mealmagician.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The household does not exist <em>or</em> the person is not a member of it. The two cases are one
 * exception on purpose: telling them apart would reveal that another family's household exists
 * (PLAN.md D8). It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class HouseholdNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param householdId the id that was asked for; kept out of the message's audience (the error
   *     page shows no detail) but useful in logs
   */
  public HouseholdNotFoundException(long householdId) {
    super("No household " + householdId + " for this person");
  }

  /** Creates the exception for a request that needs a household when the person has none. */
  public HouseholdNotFoundException() {
    super("The person belongs to no household");
  }
}
