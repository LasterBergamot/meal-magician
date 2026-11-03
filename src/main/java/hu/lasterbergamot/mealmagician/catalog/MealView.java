package hu.lasterbergamot.mealmagician.catalog;

import java.util.List;

/**
 * A meal as the pages show it: the stored values, cleaned, with its tag and ingredient names in
 * order. Returned by every {@link MealService} method that reads or changes a meal, so callers
 * never touch entities.
 *
 * @param id the meal's id, used in links
 * @param name the name
 * @param notes the notes, or {@code null} when there are none
 * @param recipeUrl the recipe link, or {@code null} when there is none
 * @param tags the tag names, ordered case-insensitively; empty when the meal has none
 * @param occasionTypes the occasion types the meal suits, in the order of the {@link OccasionType}
 *     constants; empty when it suits none in particular
 * @param archived whether the meal is out of the default library
 * @param version the optimistic-lock version; a page that edits the meal sends it back so a stale
 *     form is noticed
 * @param ingredients the ingredient names as first typed, ordered by their accent-free key; empty
 *     when the meal has none. Names only: there are no amounts.
 */
public record MealView(
    long id,
    String name,
    String notes,
    String recipeUrl,
    List<String> tags,
    List<OccasionType> occasionTypes,
    boolean archived,
    long version,
    List<String> ingredients) {}
