package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence for the occasion types of meals. Every query is scoped by the household. */
public interface MealOccasionTypeRepository
    extends JpaRepository<MealOccasionType, MealOccasionTypeKey> {

  /**
   * The links of one meal, which the service compares with the wanted types when it saves a meal.
   *
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @return its links; empty for a meal that suits no occasion
   */
  @Query(
      """
      select o from MealOccasionType o
      where o.id.mealId = :mealId and o.householdId = :householdId
      """)
  List<MealOccasionType> findLinks(long householdId, long mealId);

  /**
   * Every occasion-type link in one household, for the library list (one query, not one per meal).
   *
   * @param householdId the household
   * @return one row per meal and type, in no particular order (the caller sorts by enum order)
   */
  @Query("select o from MealOccasionType o where o.householdId = :householdId")
  List<MealOccasionType> findOfHousehold(long householdId);
}
