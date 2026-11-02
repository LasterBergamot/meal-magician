package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link PickPage}: the library meals a person can put on a day, with the recently
 * planned ones first.
 *
 * <p>The order comes from {@link PlanningService#recentlyPlannedMealIds}, the same source as the
 * library page's "recent" sort. Archived meals are left out (the default search), and so are other
 * households' meals, because {@link MealService#search} is scoped by household.
 */
@Component
class PickPageFactory {

  /** How many recently planned meals are asked for; a household's library is far smaller. */
  private static final int RECENT_LIMIT = 500;

  private final PlanningService planning;
  private final MealService meals;
  private final Messages messages;

  PickPageFactory(PlanningService planning, MealService meals, Messages messages) {
    this.planning = planning;
    this.meals = meals;
    this.messages = messages;
  }

  /**
   * Builds the sheet's content.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose library is listed
   * @param date the day the meal will be planned for
   * @param type the slot type it will be planned for
   * @param query the search text; may be blank
   * @param quick the state of the "Create and add" form
   * @return the page model, with at most {@link PickPage#LIMIT} meals
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  PickPage page(
      long userId, long householdId, LocalDate date, SlotType type, String query, PickQuick quick) {
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
    boolean more = ordered.size() > PickPage.LIMIT;
    List<MealView> shown = ordered.subList(0, Math.min(PickPage.LIMIT, ordered.size()));
    boolean libraryEmpty = ordered.isEmpty() && q.isEmpty();
    Locale locale = messages.locale();
    String dayLabel =
        messages.t("plan.day", PlanDates.dayName(date, locale), PlanDates.dateLabel(date, locale));
    return new PickPage(date, type, dayLabel, q, shown, more, libraryEmpty, quick);
  }
}
