package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for meals. Every method takes the household id, so a meal of another household is
 * never returned: it is "not found" (persistence.md).
 */
public interface MealRepository extends JpaRepository<Meal, Long> {

  /**
   * The tenant-scoped lookup: a meal id from another household finds nothing.
   *
   * @param id the meal id
   * @param householdId the household the caller belongs to
   * @return the meal, or empty if it does not exist or belongs to another household
   */
  Optional<Meal> findByIdAndHouseholdId(long id, long householdId);

  /**
   * The library search. Each filter is optional: a {@code null} pattern or tag id means "no
   * filter". The name is matched case-insensitively and the tag through a subquery, so a meal with
   * several tags appears once.
   *
   * @param householdId the household to search
   * @param namePattern a {@code LIKE} pattern from {@code MealRules.namePattern}, or {@code null}
   * @param tagId the tag a meal must have, or {@code null} for any
   * @param includeArchived whether archived meals are returned too
   * @return the matching meals, by name then id so the order is stable
   */
  @Query(
      """
      select m from Meal m
      where m.householdId = :householdId
        and (:namePattern is null or lower(m.name) like :namePattern escape '!')
        and (:tagId is null or exists (
              select mt.id from MealTag mt where mt.id.mealId = m.id and mt.id.tagId = :tagId))
        and (:includeArchived = true or m.archived = false)
      order by lower(m.name), m.id
      """)
  List<Meal> search(long householdId, String namePattern, Long tagId, boolean includeArchived);
}
