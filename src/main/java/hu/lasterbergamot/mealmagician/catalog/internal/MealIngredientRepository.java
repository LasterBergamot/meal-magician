package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence for the links between meals and ingredients. Every query is scoped by household. */
public interface MealIngredientRepository extends JpaRepository<MealIngredient, MealIngredientKey> {

  /**
   * The links of one meal, which the service compares with the wanted ingredients when it saves a
   * meal.
   *
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return its links; empty for a meal without ingredients
   */
  @Query(
      """
      select mi from MealIngredient mi
      where mi.id.mealId = :mealId and mi.householdId = :householdId
      """)
  List<MealIngredient> findLinks(long householdId, long mealId);

  /**
   * The ingredient names of one meal, in order.
   *
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return the names ordered by their folded key; empty for a meal without ingredients
   */
  @Query(
      """
      select i.name from MealIngredient mi join Ingredient i on i.id = mi.id.ingredientId
      where mi.id.mealId = :mealId and mi.householdId = :householdId
      order by i.nameKey
      """)
  List<String> findIngredientNames(long householdId, long mealId);

  /**
   * Every meal's ingredient names in one household, for the library list (one query, not one per
   * meal).
   *
   * @param householdId the household
   * @return one row per meal and ingredient, ordered by the folded key
   */
  @Query(
      """
      select new hu.lasterbergamot.mealmagician.catalog.internal.MealIngredientName(
          mi.id.mealId, i.name)
      from MealIngredient mi join Ingredient i on i.id = mi.id.ingredientId
      where mi.householdId = :householdId
      order by i.nameKey
      """)
  List<MealIngredientName> findIngredientNamesOfHousehold(long householdId);
}
