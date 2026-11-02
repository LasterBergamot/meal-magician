package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for plan templates. Every method takes the household id, so a template of another
 * household is never returned: it is "not found" (persistence.md).
 */
public interface PlanTemplateRepository extends JpaRepository<PlanTemplate, Long> {

  /**
   * The tenant-scoped lookup: a template id from another household finds nothing.
   *
   * @param id the template id
   * @param householdId the household the caller belongs to
   * @return the template, or empty if it does not exist or belongs to another household
   */
  Optional<PlanTemplate> findByIdAndHouseholdId(long id, long householdId);

  /**
   * Every plan template of the household, in no particular order; the service sorts them.
   *
   * @param householdId the household
   * @return its templates, of both scopes
   */
  List<PlanTemplate> findByHouseholdId(long householdId);
}
