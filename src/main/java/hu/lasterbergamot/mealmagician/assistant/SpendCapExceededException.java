package hu.lasterbergamot.mealmagician.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The household has used up its AI allowance for this calendar month (PLAN.md D32). It is thrown
 * <em>before</em> any call to the model, so nothing was sent to Google and nothing was charged. A
 * page catches it to show a message; left uncaught it answers with HTTP 429. The allowance starts
 * again on the first day of next month (Europe/Budapest).
 *
 * <p>The message holds only amounts, never a prompt or a person.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class SpendCapExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final long usedMicroUsd;
  private final long capMicroUsd;

  /**
   * Creates the exception.
   *
   * @param usedMicroUsd what the household has used this month, in micro-USD (1 USD = 1 000 000)
   * @param capMicroUsd the monthly allowance, in micro-USD
   */
  public SpendCapExceededException(long usedMicroUsd, long capMicroUsd) {
    super(
        "The monthly AI allowance is used up: "
            + usedMicroUsd
            + " of "
            + capMicroUsd
            + " micro-USD");
    this.usedMicroUsd = usedMicroUsd;
    this.capMicroUsd = capMicroUsd;
  }

  /**
   * What the household has used this month.
   *
   * @return micro-USD (1 USD = 1 000 000)
   */
  public long usedMicroUsd() {
    return usedMicroUsd;
  }

  /**
   * The household's monthly allowance.
   *
   * @return micro-USD (1 USD = 1 000 000)
   */
  public long capMicroUsd() {
    return capMicroUsd;
  }
}
