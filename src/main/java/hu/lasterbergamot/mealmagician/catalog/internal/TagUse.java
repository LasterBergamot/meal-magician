package hu.lasterbergamot.mealmagician.catalog.internal;

/**
 * One row of the "which tags does the household use, and how much" query: a tag name with the
 * number of meals that carry it. A projection, so the suggestions can be ranked without loading the
 * entities or the links.
 *
 * @param name the tag's name as it was first typed
 * @param nameKey the tag's lower-case key (ties are broken by it, so the order is stable)
 * @param meals how many of the household's meals carry the tag; always at least 1
 */
public record TagUse(String name, String nameKey, long meals) {}
