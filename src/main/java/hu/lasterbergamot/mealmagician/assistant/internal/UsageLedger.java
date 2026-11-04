package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.MonthlyUsage;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The per-household spend cap of the assistant (PLAN.md D32): what a household has used this
 * calendar month, a check before every call, and an atomic add after it.
 *
 * <p>The month is the first day of the month on the application clock (Europe/Budapest), so a new
 * month starts at zero by itself: there is no reset job (the app has no scheduler, D11).
 *
 * <p>The cap is checked <em>before</em> a call and usage is added <em>after</em>, so two calls that
 * start together can each pass the check and overshoot the cap by one call. That is accepted: the
 * cap is a cost guard, and Google's own budget alert still applies. It never loses usage though:
 * {@link #record} is a single {@code x = x + n} statement.
 *
 * <p>This class logs nothing about requests. It never sees a prompt or an answer, only counts.
 */
@Component
public class UsageLedger {

  private final UsageRepository usage;
  private final AiProperties properties;
  private final Clock clock;
  private final TransactionTemplate writes;

  /**
   * Creates the ledger.
   *
   * @param usage persistence for the usage rows
   * @param properties the {@code mm.ai.*} settings (cap and prices)
   * @param clock the application clock, which decides what "this month" is
   * @param transactions used to run each write in a transaction of its own
   */
  public UsageLedger(
      UsageRepository usage,
      AiProperties properties,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.usage = usage;
    this.properties = properties;
    this.clock = clock;
    // A new transaction even if the caller has one: money that was spent stays recorded when the
    // caller's own work rolls back, and a failure here cannot poison the caller's transaction.
    this.writes = new TransactionTemplate(transactions);
    this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Refuses the call when the household has used up this month's allowance.
   *
   * @param householdId the household that is about to make a call
   * @throws SpendCapExceededException if this month's cost is at or over {@code
   *     mm.ai.monthly-cap-usd}; the caller must then make no call at all
   */
  public void assertWithinCap(long householdId) {
    MonthlyUsage now = usageThisMonth(householdId);
    if (now.capReached()) {
      throw new SpendCapExceededException(now.costMicroUsd(), now.capMicroUsd());
    }
  }

  /**
   * Adds one call's usage to the household's month, atomically.
   *
   * @param householdId the household that made the call
   * @param inputTokens tokens sent
   * @param outputTokens tokens received, "thinking" tokens included (they are billed as output)
   * @return the cost of this call in micro-USD, rounded up
   * @throws IllegalStateException if the prices are not configured
   */
  public long record(long householdId, long inputTokens, long outputTokens) {
    long cost = properties.costMicroUsd(inputTokens, outputTokens);
    LocalDate month = currentMonth();
    writes.executeWithoutResult(status -> {
      usage.insertIfAbsent(householdId, month);
      usage.add(householdId, month, inputTokens, outputTokens, cost);
    });
    return cost;
  }

  /**
   * Reads what the household has used this month.
   *
   * @param householdId the household
   * @return the counters and the allowance; all counters zero when nothing was used yet
   */
  public MonthlyUsage usageThisMonth(long householdId) {
    LocalDate month = currentMonth();
    long cap = properties.capMicroUsd();
    return usage
        .findByHouseholdIdAndMonth(householdId, month)
        .map(row ->
            new MonthlyUsage(
                month, row.getInputTokens(), row.getOutputTokens(), row.getCostMicroUsd(), cap))
        .orElseGet(() -> new MonthlyUsage(month, 0, 0, 0, cap));
  }

  private LocalDate currentMonth() {
    return LocalDate.now(clock).withDayOfMonth(1);
  }
}
