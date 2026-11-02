package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for plan template items. Every method takes the household id, so an item of another
 * household is never returned (persistence.md).
 */
public interface PlanTemplateItemRepository extends JpaRepository<PlanTemplateItem, Long> {

  /**
   * The items of one template, in no particular order; the service sorts them.
   *
   * @param templateId the template
   * @param householdId the household the caller belongs to
   * @return the items, empty if the template is not in this household
   */
  List<PlanTemplateItem> findByTemplateIdAndHouseholdId(long templateId, long householdId);

  /**
   * Every template item of the household, so the list can count items per template in one query.
   *
   * @param householdId the household
   * @return all its template items
   */
  List<PlanTemplateItem> findByHouseholdId(long householdId);
}
