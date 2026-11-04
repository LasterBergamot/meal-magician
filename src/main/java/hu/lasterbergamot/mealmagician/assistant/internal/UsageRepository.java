package hu.lasterbergamot.mealmagician.assistant.internal;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for the spend ledger. Every method takes the household id, and the writes are single
 * statements, so no caller can lose an update.
 */
public interface UsageRepository extends JpaRepository<UsageEntity, UsageEntity.Key> {

  /**
   * Reads one household's usage for a month.
   *
   * @param householdId the household
   * @param month the first day of the month
   * @return the row, or empty when nothing has been used (which means zero)
   */
  Optional<UsageEntity> findByHouseholdIdAndMonth(long householdId, LocalDate month);

  /**
   * Creates the month's row with zero counters unless it exists. Like the tag and ingredient
   * writers it uses {@code ON CONFLICT DO NOTHING}: two calls finishing at once both succeed, and
   * the loser does not abort a Postgres transaction the way a failed insert would.
   *
   * @param householdId the household
   * @param month the first day of the month
   * @return 1 if the row was created, 0 if it already existed
   */
  @Modifying
  @Query(
      value =
          """
          insert into assistant_usage (household_id, "month")
          values (:householdId, :month)
          on conflict do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(long householdId, LocalDate month);

  /**
   * Adds to the month's counters in one atomic statement ({@code x = x + n}), so concurrent calls
   * never lose each other's usage. The row must exist: call {@link #insertIfAbsent} first.
   *
   * @param householdId the household
   * @param month the first day of the month
   * @param inputTokens tokens to add to the input counter
   * @param outputTokens tokens to add to the output counter
   * @param costMicroUsd micro-USD to add to the cost
   * @return the number of rows changed (1 when the row exists)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update UsageEntity u
      set u.inputTokens = u.inputTokens + :inputTokens,
          u.outputTokens = u.outputTokens + :outputTokens,
          u.costMicroUsd = u.costMicroUsd + :costMicroUsd
      where u.householdId = :householdId and u.month = :month
      """)
  int add(
      long householdId, LocalDate month, long inputTokens, long outputTokens, long costMicroUsd);
}
