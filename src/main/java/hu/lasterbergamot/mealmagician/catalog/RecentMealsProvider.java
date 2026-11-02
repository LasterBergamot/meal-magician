package hu.lasterbergamot.mealmagician.catalog;

import java.util.List;

/**
 * "Which meals did the household plan lately?", asked by the meal library to sort its list with the
 * recently used meals first.
 *
 * <p><b>Why an interface in the catalog.</b> The answer lives in the {@code planning} module (its
 * {@code meal_slot} table), but {@code planning} already depends on {@code catalog}, so the catalog
 * cannot call {@code PlanningService} without a module cycle. This is dependency inversion: the
 * catalog declares what it needs, and {@code planning} provides the bean that implements it. The
 * arrow between the modules stays {@code planning -> catalog}, and {@code ModularityTests} stays
 * green. The catalog's pages treat the bean as optional, so the library still works (newest
 * additions first) where no planner is present.
 */
public interface RecentMealsProvider {

  /**
   * The meals planned on or before today, most recently planned first.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household whose plan it is
   * @param limit how many ids to return at most; at least 1
   * @return distinct meal ids, most recent first; empty when nothing was planned yet
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  List<Long> recentMealIds(long userId, long householdId, int limit);
}
