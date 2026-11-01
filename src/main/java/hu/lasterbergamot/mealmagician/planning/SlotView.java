package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;

/**
 * One planned slot as the pages show it. It carries the meal's id and not its name: the week view
 * gets names from the catalog's {@code MealService} (one search for the whole library), so the
 * planner does not copy meal data that could go stale.
 *
 * @param id the slot's id, used in links and in the actions that change it
 * @param date the day the meal is planned for
 * @param slotType breakfast, lunch, dinner or snack
 * @param position the order inside its date and slot type, counted from 0
 * @param mealId the library meal planned here; it may be archived, see {@link PlanningService}
 * @param note the note for this slot, or {@code null} when there is none
 * @param version the optimistic-lock version; a page sends it back with every change so a stale
 *     view is refused with {@link PlanConflictException}
 */
public record SlotView(
    long id,
    LocalDate date,
    SlotType slotType,
    int position,
    long mealId,
    String note,
    long version) {}
