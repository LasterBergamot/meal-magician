package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.time.LocalDate;

/**
 * One dated occasion as a share-link visitor sees it. Deliberately minimal (PLAN.md D28): no id, no
 * notes and no menu.
 *
 * @param date the day of the occasion
 * @param name the occasion's name
 * @param type the kind of occasion
 */
public record SharedOccasion(LocalDate date, String name, OccasionType type) {}
