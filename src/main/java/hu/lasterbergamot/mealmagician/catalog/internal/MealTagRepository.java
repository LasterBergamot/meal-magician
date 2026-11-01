package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence for the links between meals and tags. */
public interface MealTagRepository extends JpaRepository<MealTag, MealTagKey> {

  /**
   * The links of one meal, which the service compares with the wanted tags when it saves a meal.
   *
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return its links; empty for a meal without tags
   */
  @Query(
      """
      select mt from MealTag mt
      where mt.id.mealId = :mealId and mt.householdId = :householdId
      """)
  List<MealTag> findLinks(long householdId, long mealId);

  /**
   * The tag names of one meal, in order.
   *
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return the names ordered by their lower-case form; empty for a meal without tags
   */
  @Query(
      """
      select t.name from MealTag mt join Tag t on t.id = mt.id.tagId
      where mt.id.mealId = :mealId and mt.householdId = :householdId
      order by t.nameKey
      """)
  List<String> findTagNames(long householdId, long mealId);

  /**
   * Every meal's tag names in one household, for the library list (one query, not one per meal).
   *
   * @param householdId the household
   * @return one row per meal and tag, ordered by tag name within the household
   */
  @Query(
      """
      select new hu.lasterbergamot.mealmagician.catalog.internal.MealTagName(mt.id.mealId, t.name)
      from MealTag mt join Tag t on t.id = mt.id.tagId
      where mt.householdId = :householdId
      order by t.nameKey
      """)
  List<MealTagName> findTagNamesOfHousehold(long householdId);
}
