package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.List;

/**
 * One occasion template with its menu.
 *
 * @param id the template's id
 * @param name the trimmed name
 * @param type the kind of occasion an occasion made from it gets
 * @param notes the notes copied to a new occasion, or {@code null} when there are none
 * @param menu the meals, ordered by position, then id
 */
public record OccasionTemplateView(
    long id, String name, OccasionType type, String notes, List<MenuItemView> menu) {}
