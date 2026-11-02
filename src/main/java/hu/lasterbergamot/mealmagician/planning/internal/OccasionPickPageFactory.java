package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link OccasionPickPage}: the library meals a person can put on an occasion's menu,
 * with the recently planned ones first. The ranking is exactly the plan's pick sheet's ({@link
 * PickPageFactory}), so both sheets feel the same.
 *
 * <p>Archived meals are left out (the default search), and so are other households' meals, because
 * {@link MealService#search} is scoped by household.
 */
@Component
class OccasionPickPageFactory {

  /** How many recently planned meals are asked for; a household's library is far smaller. */
  private static final int RECENT_LIMIT = 500;

  private final PlanningService planning;
  private final MealService meals;
  private final OccasionService occasions;

  OccasionPickPageFactory(PlanningService planning, MealService meals, OccasionService occasions) {
    this.planning = planning;
    this.meals = meals;
    this.occasions = occasions;
  }

  /**
   * Builds the sheet's content.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose library is listed
   * @param occasionId the occasion the meal will be added to
   * @param query the search text; may be blank
   * @param quick the state of the "Create and add" form
   * @return the page model, with at most {@link OccasionPickPage#LIMIT} meals
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household; this is checked first
   */
  OccasionPickPage page(
      long userId, long householdId, long occasionId, String query, PickQuick quick) {
    occasions.find(userId, householdId, occasionId);
    String q = query.strip();
    List<MealView> found = meals.search(userId, householdId, new MealSearch(q, null, false));
    List<Long> recent = planning.recentlyPlannedMealIds(userId, householdId, RECENT_LIMIT);
    Map<Long, Integer> rank = new HashMap<>();
    for (int i = 0; i < recent.size(); i++) {
      rank.putIfAbsent(recent.get(i), i);
    }
    List<MealView> ordered =
        found.stream()
            .sorted(
                Comparator.comparingInt(
                        (MealView meal) -> rank.getOrDefault(meal.id(), Integer.MAX_VALUE))
                    .thenComparing(MealView::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    boolean more = ordered.size() > OccasionPickPage.LIMIT;
    List<MealView> shown = ordered.subList(0, Math.min(OccasionPickPage.LIMIT, ordered.size()));
    boolean libraryEmpty = ordered.isEmpty() && q.isEmpty();
    return new OccasionPickPage(occasionId, q, shown, more, libraryEmpty, quick);
  }
}
