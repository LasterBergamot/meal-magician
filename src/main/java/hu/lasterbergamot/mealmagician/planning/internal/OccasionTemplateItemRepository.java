package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for occasion template menu items. Every method takes the household id, so an item of
 * another household is never returned (persistence.md).
 */
public interface OccasionTemplateItemRepository extends JpaRepository<OccasionTemplateItem, Long> {

  /**
   * The menu of one occasion template.
   *
   * @param templateId the template
   * @param householdId the household the caller belongs to
   * @return the items by position, then id; empty if the template is not in this household
   */
  List<OccasionTemplateItem> findByTemplateIdAndHouseholdIdOrderByPositionAscIdAsc(
      long templateId, long householdId);

  /**
   * Every occasion template item of the household, so the list can count items per template in one
   * query.
   *
   * @param householdId the household
   * @return all its occasion template items
   */
  List<OccasionTemplateItem> findByHouseholdId(long householdId);
}
