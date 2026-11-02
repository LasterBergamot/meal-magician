package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for occasion menu items. Every method takes the household id, so an item of another
 * household is never returned (persistence.md).
 */
public interface OccasionItemRepository extends JpaRepository<OccasionItem, Long> {

  /**
   * The menu of one occasion.
   *
   * @param occasionId the occasion
   * @param householdId the household the caller belongs to
   * @return the items by position, then id; empty if the occasion is not in this household
   */
  List<OccasionItem> findByOccasionIdAndHouseholdIdOrderByPositionAscIdAsc(
      long occasionId, long householdId);

  /**
   * One menu item, which must be on the given occasion: an id from another occasion finds nothing.
   *
   * @param id the item id
   * @param occasionId the occasion it must belong to
   * @param householdId the household the caller belongs to
   * @return the item, or empty if it is not there
   */
  Optional<OccasionItem> findByIdAndOccasionIdAndHouseholdId(
      long id, long occasionId, long householdId);

  /**
   * Every menu item of the household, so lists can count items per occasion in one query.
   *
   * @param householdId the household
   * @return all its menu items
   */
  List<OccasionItem> findByHouseholdId(long householdId);
}
