package hu.lasterbergamot.mealmagician.planning.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for occasions. Every method takes the household id, so an occasion of another
 * household is never returned: it is "not found" (persistence.md).
 */
public interface OccasionRepository extends JpaRepository<Occasion, Long> {

  /**
   * The tenant-scoped lookup: an occasion id from another household finds nothing.
   *
   * @param id the occasion id
   * @param householdId the household the caller belongs to
   * @return the occasion, or empty if it does not exist or belongs to another household
   */
  Optional<Occasion> findByIdAndHouseholdId(long id, long householdId);

  /**
   * Every occasion of the household.
   *
   * @param householdId the household
   * @return its occasions by date, then id
   */
  List<Occasion> findByHouseholdIdOrderByOccasionDateAscIdAsc(long householdId);

  /**
   * The occasions on the given dates, for the week strip. Runs on {@code
   * ix_occasion_household_date}.
   *
   * @param householdId the household
   * @param from the first date, inclusive
   * @param to the last date, inclusive
   * @return the occasions by date, then id
   */
  List<Occasion> findByHouseholdIdAndOccasionDateBetweenOrderByOccasionDateAscIdAsc(
      long householdId, LocalDate from, LocalDate to);
}
