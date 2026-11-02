package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.RecentMealsProvider;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Answers the meal library's "recently used first" question from the plan. The catalog declares
 * {@link RecentMealsProvider} and the planner implements it here, so the module arrow stays {@code
 * planning -> catalog} (the catalog could not call {@link PlanningService} without a cycle).
 */
@Component
class PlanningRecentMeals implements RecentMealsProvider {

  private final PlanningService planning;

  PlanningRecentMeals(PlanningService planning) {
    this.planning = planning;
  }

  /** Delegates to {@link PlanningService#recentlyPlannedMealIds}. */
  @Override
  public List<Long> recentMealIds(long userId, long householdId, int limit) {
    return planning.recentlyPlannedMealIds(userId, householdId, limit);
  }
}
