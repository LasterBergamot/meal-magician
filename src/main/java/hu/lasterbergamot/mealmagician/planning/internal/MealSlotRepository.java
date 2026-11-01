package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for planned slots. Every method takes the household id, so a slot of another
 * household is never returned: it is "not found" (persistence.md).
 */
public interface MealSlotRepository extends JpaRepository<MealSlot, Long> {

  /**
   * The tenant-scoped lookup: a slot id from another household finds nothing.
   *
   * @param id the slot id
   * @param householdId the household the caller belongs to
   * @return the slot, or empty if it does not exist or belongs to another household
   */
  Optional<MealSlot> findByIdAndHouseholdId(long id, long householdId);

  /**
   * The week view's query: every slot of the household on the given dates, both ends included. The
   * service passes Monday and Sunday, so this is one range scan on {@code ix_meal_slot_week}.
   *
   * @param householdId the household
   * @param from the first date, inclusive
   * @param to the last date, inclusive
   * @return the slots in no particular order; the service sorts them for the view
   */
  List<MealSlot> findByHouseholdIdAndSlotDateBetween(
      long householdId, LocalDate from, LocalDate to);

  /**
   * One group of the plan: the slots of a date and slot type, in their place order. The service
   * uses it to append, to close a gap after a move or remove, and to reorder.
   *
   * @param householdId the household
   * @param date the day
   * @param slotType the slot type
   * @return the group by position, then id so equal positions have a stable order
   */
  List<MealSlot> findByHouseholdIdAndSlotDateAndSlotTypeOrderByPositionAscIdAsc(
      long householdId, LocalDate date, SlotType slotType);

  /**
   * The meals the household planned on or before {@code today}, most recent first. A meal planned
   * for next week has not been used yet, so future dates do not count. Each meal appears once, at
   * its latest date.
   *
   * @param householdId the household
   * @param today the latest date that counts as "used"
   * @param page how many ids to return, from the first row
   * @return the meal ids, most recently planned first; empty when nothing was planned yet
   */
  @Query(
      """
      select s.mealId from MealSlot s
      where s.householdId = :householdId and s.slotDate <= :today
      group by s.mealId
      order by max(s.slotDate) desc, max(s.id) desc
      """)
  List<Long> findRecentMealIds(long householdId, LocalDate today, Pageable page);
}
