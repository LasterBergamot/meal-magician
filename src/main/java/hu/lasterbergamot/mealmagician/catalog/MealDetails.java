package hu.lasterbergamot.mealmagician.catalog;

import java.util.Collection;
import java.util.List;

/**
 * What the household types in when it creates or edits a meal, before it is checked. Blanks around
 * the values are fine; the service cleans them and throws {@link InvalidMealException} for anything
 * that breaks a rule.
 *
 * @param name the meal's name, required; at most 200 characters
 * @param notes free text about the meal, optional; at most 2000 characters
 * @param recipeUrl a link to the recipe, optional; must be an {@code http} or {@code https} address
 * @param tags the tag names to put on the meal; {@code null} means none. Names that differ only in
 *     letter case are the same tag, and the first spelling wins.
 * @param occasionTypes the occasion types the meal suits; {@code null} means none. Like the tags,
 *     this is the complete set on an edit: a type left out is removed from the meal.
 * @param ingredients the ingredient names to put on the meal; {@code null} means none. Each is at
 *     most 60 characters and a meal has at most 30. Names that differ only in case or accents
 *     ("Tojás", "tojas") are the same ingredient, and the first spelling wins. Like the tags, this
 *     is the complete set on an edit.
 */
public record MealDetails(
    String name,
    String notes,
    String recipeUrl,
    List<String> tags,
    Collection<OccasionType> occasionTypes,
    List<String> ingredients) {

  /**
   * Details for a meal that suits no particular occasion and has no ingredients: the form every
   * caller used before occasion types and ingredients existed, and what a quick add means.
   *
   * @param name the meal's name
   * @param notes the notes, may be {@code null}
   * @param recipeUrl the recipe link, may be {@code null}
   * @param tags the tag names, may be {@code null}
   */
  public MealDetails(String name, String notes, String recipeUrl, List<String> tags) {
    this(name, notes, recipeUrl, tags, null, List.of());
  }

  /**
   * Details with occasion types but no ingredients: the shape callers used before ingredients
   * existed.
   *
   * @param name the meal's name
   * @param notes the notes, may be {@code null}
   * @param recipeUrl the recipe link, may be {@code null}
   * @param tags the tag names, may be {@code null}
   * @param occasionTypes the occasion types, may be {@code null}
   */
  public MealDetails(
      String name,
      String notes,
      String recipeUrl,
      List<String> tags,
      Collection<OccasionType> occasionTypes) {
    this(name, notes, recipeUrl, tags, occasionTypes, List.of());
  }
}
