package hu.lasterbergamot.mealmagician.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The meal does not exist <em>or</em> it belongs to another household. One exception for both, so a
 * stranger cannot tell which meal ids exist (PLAN.md D8). It answers with HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MealNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param mealId the id that was asked for; kept out of the error page but useful in logs
   */
  public MealNotFoundException(long mealId) {
    super("No meal " + mealId + " in this household");
  }
}
