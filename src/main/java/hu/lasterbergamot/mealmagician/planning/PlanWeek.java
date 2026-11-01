package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;
import java.util.List;

/**
 * One ISO week of the plan: the slots on its seven dates, in the order the week view shows them.
 *
 * @param monday the first day of the week; weeks start on Monday (ISO 8601)
 * @param slots the household's slots from Monday to Sunday, ordered by date, then slot type
 *     (breakfast, lunch, dinner, snack), then position; empty for a week with no plan
 */
public record PlanWeek(LocalDate monday, List<SlotView> slots) {}
