package hu.lasterbergamot.mealmagician.assistant;

import java.time.LocalDate;

/**
 * What one household has used of the assistant in one calendar month, next to its allowance.
 *
 * @param month the first day of the month (Europe/Budapest)
 * @param inputTokens tokens sent to the model
 * @param outputTokens tokens received, including the model's "thinking" tokens, which are billed as
 *     output
 * @param costMicroUsd the cost so far, in micro-USD (1 USD = 1 000 000), rounded up per call
 * @param capMicroUsd the monthly allowance, in micro-USD
 */
public record MonthlyUsage(
    LocalDate month, long inputTokens, long outputTokens, long costMicroUsd, long capMicroUsd) {

  /**
   * Whether the allowance is used up, which is when the next call would be refused.
   *
   * @return {@code true} when the cost has reached the allowance
   */
  public boolean capReached() {
    return costMicroUsd >= capMicroUsd;
  }
}
