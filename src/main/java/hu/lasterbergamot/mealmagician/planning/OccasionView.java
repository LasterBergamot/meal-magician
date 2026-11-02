package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.time.LocalDate;
import java.util.List;

/**
 * One occasion with its menu, for the occasion's detail page.
 *
 * @param id the occasion's id
 * @param name the trimmed name
 * @param date the day of the occasion
 * @param type the kind of occasion
 * @param notes the notes, or {@code null} when there are none
 * @param menu the meals, ordered by position, then id
 * @param version the optimistic-lock version; a page sends it back with an edit or delete
 */
public record OccasionView(
    long id,
    String name,
    LocalDate date,
    OccasionType type,
    String notes,
    List<MenuItemView> menu,
    long version) {}
