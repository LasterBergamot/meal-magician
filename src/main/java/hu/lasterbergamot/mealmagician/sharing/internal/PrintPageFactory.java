package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanWeek;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SharedOccasion;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link PrintPage} for signed-in members. It reads through the checked public services
 * ({@link PlanningService}, {@link MealService}, {@link OccasionService}), so a person who is not a
 * member of the household gets a 404 before any data is read. That is the difference from the
 * public share page, which uses the unchecked {@code SharedWeekReader}: the print page also shows
 * the slot notes, which a link holder must never see.
 */
@Component
class PrintPageFactory {

  private final PlanningService planning;
  private final MealService meals;
  private final OccasionService occasions;
  private final Messages messages;

  PrintPageFactory(
      PlanningService planning, MealService meals, OccasionService occasions, Messages messages) {
    this.planning = planning;
    this.meals = meals;
    this.occasions = occasions;
    this.messages = messages;
  }

  /**
   * Builds the print page of one week.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose plan is printed
   * @param monday the Monday of the week
   * @return the page, with only the meal types that hold a meal on each day (PLAN.md D30)
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  PrintPage page(long userId, long householdId, LocalDate monday) {
    PlanWeek planned = planning.week(userId, householdId, monday);
    // A planned slot always has its meal: the database refuses to delete a planned meal. Archived
    // meals are included because a past plan may still hold one.
    Map<Long, String> names =
        meals.search(userId, householdId, new MealSearch(null, null, true)).stream()
            .collect(Collectors.toMap(MealView::id, MealView::name));
    List<SharedWeekPage.PrintMeal> printed =
        planned.slots().stream()
            .map(slot ->
                new SharedWeekPage.PrintMeal(
                    slot.date(), slot.slotType(), names.get(slot.mealId()), slot.note()))
            .toList();
    List<SharedOccasion> shared =
        occasions.between(userId, householdId, monday, monday.plusDays(6)).stream()
            .map(o -> new SharedOccasion(o.date(), o.name(), o.type()))
            .toList();
    return new PrintPage(
        SharedWeekPage.forPrint(monday, printed, shared, messages),
        "/plan?week=" + ShareForm.codeOf(monday));
  }
}
