package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanWeek;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link WeekPage}: reads one week from {@link PlanningService}, joins each slot with
 * its meal's name from {@link MealService}, and formats every date and heading in the reader's
 * language.
 *
 * <p>The planner keeps only meal ids, so the names come from one search of the whole library
 * (archived meals included) instead of one lookup per slot. Both services check that the person is
 * a member of the household, so a stranger never gets past the first call.
 */
@Component
class WeekPageFactory {

  private final PlanningService planning;
  private final MealService meals;
  private final OccasionService occasions;
  private final Messages messages;
  private final Clock clock;

  WeekPageFactory(
      PlanningService planning,
      MealService meals,
      OccasionService occasions,
      Messages messages,
      Clock clock) {
    this.planning = planning;
    this.meals = meals;
    this.occasions = occasions;
    this.messages = messages;
    this.clock = clock;
  }

  /**
   * The week that contains today, by the application clock.
   *
   * @return the current ISO week
   */
  IsoWeek currentWeek() {
    return IsoWeek.containing(LocalDate.now(clock));
  }

  /**
   * Builds the page for one week.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose plan is shown
   * @param week the week to show
   * @return the page model, with seven days of four slot groups each
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  WeekPage page(long userId, long householdId, IsoWeek week) {
    PlanWeek planned = planning.week(userId, householdId, week.monday());
    // A planned slot always has its meal: the database refuses to delete a planned meal.
    Map<Long, MealView> byId =
        meals.search(userId, householdId, new MealSearch(null, null, true)).stream()
            .collect(Collectors.toMap(MealView::id, Function.identity()));
    Map<LocalDate, Map<SlotType, List<PlannedMeal>>> grouped =
        planned.slots().stream()
            .collect(
                Collectors.groupingBy(
                    SlotView::date,
                    Collectors.groupingBy(
                        SlotView::slotType,
                        Collectors.mapping(slot -> planned(slot, byId), Collectors.toList()))));

    Locale locale = messages.locale();
    LocalDate today = LocalDate.now(clock);
    List<DayPlan> days =
        week.days().stream()
            .map(
                date -> day(date, date.equals(today), grouped.getOrDefault(date, Map.of()), locale))
            .toList();
    String title = messages.t("plan.week.title", String.valueOf(week.number()));
    String range =
        messages.t(
            "plan.week.range",
            PlanDates.mediumDate(week.monday(), locale),
            PlanDates.mediumDate(week.sunday(), locale));
    List<OccasionChip> chips =
        occasions.between(userId, householdId, week.monday(), week.sunday()).stream()
            .map(o ->
                new OccasionChip(
                    o.id(),
                    o.name(),
                    messages.t(
                        "plan.day",
                        PlanDates.dayName(o.date(), locale),
                        PlanDates.dateLabel(o.date(), locale)),
                    o.type()))
            .toList();
    return new WeekPage(week, title, range, week.contains(today), days, false, chips, null);
  }

  private static PlannedMeal planned(SlotView slot, Map<Long, MealView> byId) {
    MealView meal = byId.get(slot.mealId());
    return new PlannedMeal(
        slot.id(),
        slot.mealId(),
        meal.name(),
        meal.archived(),
        slot.note(),
        slot.position(),
        slot.version());
  }

  private static DayPlan day(
      LocalDate date, boolean today, Map<SlotType, List<PlannedMeal>> byType, Locale locale) {
    List<SlotGroup> groups =
        Arrays.stream(SlotType.values())
            .map(type -> new SlotGroup(date, type, byType.getOrDefault(type, List.of())))
            .toList();
    return new DayPlan(
        date,
        PlanDates.dayName(date, locale),
        PlanDates.dateLabel(date, locale),
        PlanDates.shortName(date, locale),
        PlanDates.fullLabel(date, locale),
        today,
        groups);
  }
}
