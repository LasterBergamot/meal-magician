package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link SlotEditor} for one planned slot: the slot, its meal's name, its place in its
 * group and the choices of the move form, all in the reader's language.
 */
@Component
class SlotEditorFactory {

  private final PlanningService planning;
  private final MealService meals;
  private final Messages messages;

  SlotEditorFactory(PlanningService planning, MealService meals, Messages messages) {
    this.planning = planning;
    this.meals = meals;
    this.messages = messages;
  }

  /**
   * Builds the editor.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose plan it is
   * @param slotId the slot to edit
   * @return the editor, with no note error
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException if the slot is not in
   *     this household
   */
  SlotEditor editor(long userId, long householdId, long slotId) {
    SlotView slot = planning.slot(userId, householdId, slotId);
    MealView meal = meals.find(userId, householdId, slot.mealId());
    int groupSize =
        (int)
            planning.week(userId, householdId, slot.date()).slots().stream()
                .filter(other ->
                    other.date().equals(slot.date()) && other.slotType() == slot.slotType())
                .count();
    Locale locale = messages.locale();
    List<FormOption> dayOptions =
        IsoWeek.containing(slot.date()).days().stream()
            .map(day -> new FormOption(day.toString(), dayLabel(day, locale)))
            .toList();
    List<FormOption> slotOptions =
        Arrays.stream(SlotType.values())
            .map(type -> new FormOption(type.name(), messages.t(SlotGroup.labelKeyOf(type))))
            .toList();
    return new SlotEditor(
        slot.id(),
        slot.version(),
        meal.id(),
        meal.name(),
        meal.archived(),
        slot.date(),
        slot.slotType(),
        dayLabel(slot.date(), locale),
        slot.position(),
        groupSize,
        dayOptions,
        slotOptions,
        slot.note() == null ? "" : slot.note(),
        null);
  }

  private String dayLabel(LocalDate day, Locale locale) {
    return messages.t("plan.day", PlanDates.dayName(day, locale), PlanDates.dateLabel(day, locale));
  }
}
