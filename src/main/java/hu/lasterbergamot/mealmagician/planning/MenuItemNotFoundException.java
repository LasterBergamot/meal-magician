package hu.lasterbergamot.mealmagician.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The menu item does not exist on that occasion. An item that someone else already removed is "not
 * found" too, because menu items carry no version. It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MenuItemNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param itemId the id that was asked for; kept out of the error page but useful in logs
   */
  public MenuItemNotFoundException(long itemId) {
    super("No menu item " + itemId + " on this occasion");
  }
}
