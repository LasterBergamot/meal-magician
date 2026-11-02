package hu.lasterbergamot.mealmagician.planning.internal;

/**
 * A meal planned in one slot, as the week page prints it: the planner's slot joined with the meal's
 * name from the library.
 *
 * @param slotId the slot's id; the edit actions of the next issue address it
 * @param mealId the library meal
 * @param mealName the meal's name, escaped by the template
 * @param archived whether the meal is archived; it still shows in the plan, with a badge
 * @param note the slot's note, or {@code null} when there is none
 * @param position the place inside its date and slot type, counted from 0
 * @param version the slot's optimistic-lock version, which an edit sends back
 */
public record PlannedMeal(
    long slotId,
    long mealId,
    String mealName,
    boolean archived,
    String note,
    int position,
    long version) {}
