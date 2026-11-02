package hu.lasterbergamot.mealmagician.planning;

/**
 * One meal in a plan template. Like {@link SlotView} it carries the meal's id and not its name, so
 * the planner does not copy meal data that could go stale.
 *
 * @param id the item's id
 * @param dayOffset days after the first day: always 0 for a day template, 0 (Monday) to 6 (Sunday)
 *     for a week template
 * @param slotType breakfast, lunch, dinner or snack
 * @param position the order inside its day offset and slot type, counted from 0
 * @param mealId the library meal; it may be archived
 * @param note the note copied with the meal, or {@code null} when there is none
 */
public record PlanTemplateItemView(
    long id, int dayOffset, SlotType slotType, int position, long mealId, String note) {}
