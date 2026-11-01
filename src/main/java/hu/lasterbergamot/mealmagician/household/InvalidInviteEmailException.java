package hu.lasterbergamot.mealmagician.household;

/**
 * The address entered for an invite is not a plausible e-mail address. Controllers catch it and
 * show the form again with a message; it is not an HTTP error by itself.
 */
public class InvalidInviteEmailException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public InvalidInviteEmailException() {
    super("The invite address is not a valid e-mail address");
  }
}
