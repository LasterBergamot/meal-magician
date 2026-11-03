package hu.lasterbergamot.mealmagician.planning;

import java.time.LocalDate;
import java.util.List;

/**
 * A week of a household's plan as a share-link visitor sees it. The data minimisation of PLAN.md
 * D28 is done by this type: it holds only dates, slot types, meal names and occasion names. There
 * are no ids, notes, ratings, member names or household names, so no caller can leak them by
 * mistake.
 *
 * @param monday the first day of the shared week
 * @param meals the planned meals, ordered by date, slot type and position
 * @param occasions the occasions of the week, ordered by date
 */
public record SharedWeek(
    LocalDate monday, List<SharedMeal> meals, List<SharedOccasion> occasions) {}
