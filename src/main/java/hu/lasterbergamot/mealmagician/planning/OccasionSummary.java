package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.time.LocalDate;

/**
 * One occasion as a list or the week strip shows it, without its menu.
 *
 * @param id the occasion's id
 * @param name the trimmed name
 * @param date the day of the occasion
 * @param type the kind of occasion
 * @param itemCount how many meals are on the menu
 * @param version the optimistic-lock version
 */
public record OccasionSummary(
    long id, String name, LocalDate date, OccasionType type, int itemCount, long version) {}
