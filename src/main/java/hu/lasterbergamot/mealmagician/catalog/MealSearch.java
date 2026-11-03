package hu.lasterbergamot.mealmagician.catalog;

/**
 * The library search: what the household typed into the search box and the tag and ingredient
 * filters. Every field is optional, and an empty search returns the whole library (without archived
 * meals).
 *
 * @param text part of the name, matched case-insensitively and literally (so {@code %} and {@code
 *     _} are ordinary characters); {@code null} or blank means no name filter
 * @param tag a tag name, matched case-insensitively; {@code null} or blank means no tag filter. A
 *     tag the household does not have gives an empty result, not an error.
 * @param ingredient an ingredient name, matched ignoring case and accents ("tojas" finds "tojás");
 *     {@code null} or blank means no ingredient filter. An ingredient the household does not have
 *     gives an empty result, not an error.
 * @param includeArchived whether archived meals are listed too
 */
public record MealSearch(String text, String tag, String ingredient, boolean includeArchived) {

  /**
   * A search without an ingredient filter: the form every caller used before ingredients existed.
   *
   * @param text part of the name, may be {@code null}
   * @param tag a tag name, may be {@code null}
   * @param includeArchived whether archived meals are listed too
   */
  public MealSearch(String text, String tag, boolean includeArchived) {
    this(text, tag, null, includeArchived);
  }
}
