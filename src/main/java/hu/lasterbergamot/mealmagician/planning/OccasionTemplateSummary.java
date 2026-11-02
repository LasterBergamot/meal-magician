package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;

/**
 * One occasion template as the template list shows it, without its menu.
 *
 * @param id the template's id
 * @param name the trimmed name
 * @param type the kind of occasion an occasion made from it gets
 * @param itemCount how many meals are on its menu
 */
public record OccasionTemplateSummary(long id, String name, OccasionType type, int itemCount) {}
