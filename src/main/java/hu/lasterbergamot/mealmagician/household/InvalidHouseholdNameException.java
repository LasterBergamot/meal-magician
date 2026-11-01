package hu.lasterbergamot.mealmagician.household;

/**
 * The submitted household name is empty or too long. Controllers catch it and show the form again
 * with a message; it is not an HTTP error by itself.
 */
public class InvalidHouseholdNameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public InvalidHouseholdNameException() {
    super("A household name needs 1 to " + HouseholdService.MAX_NAME_LENGTH + " characters");
  }
}
