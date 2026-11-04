package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.IngredientSuggestion;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the ingredient picker's list shows for one search: the suggested ingredients and, when
 * nothing in them is the text the person typed, an "Add" row for a brand new name.
 *
 * <p>The add row is what lets a name that is not in any list be picked with one tap. It is left out
 * when a suggestion already is that name (ignoring case and accents), because adding "tojas" next
 * to a listed "Tojás" would only make a duplicate that the service then merges.
 *
 * @param suggestions the matches, the household's own ingredients first (see {@code
 *     MealService#ingredientSuggestions}); never {@code null}
 * @param addName the cleaned text to offer as a new ingredient, or {@code null} when there is no
 *     add row (nothing typed, or a suggestion already has this name)
 */
public record IngredientOptions(List<IngredientSuggestion> suggestions, String addName) {

  /** The combining accent marks that {@link Normalizer} splits off a letter. */
  private static final Pattern MARKS = Pattern.compile("\\p{M}+");

  /**
   * Builds the options for the text typed so far.
   *
   * @param typed the search text, may be {@code null} or blank
   * @param suggestions the matches the service found for it
   * @return the options; {@link #addName()} is the typed text with blanks trimmed and collapsed
   *     when it is not blank and no suggestion has the same folded name
   */
  public static IngredientOptions of(String typed, List<IngredientSuggestion> suggestions) {
    String cleaned = typed == null ? "" : typed.strip().replaceAll("\\s+", " ");
    if (cleaned.isEmpty()) {
      return new IngredientOptions(suggestions, null);
    }
    // Same folding as the catalog's MealRules.ingredientKey, which is package-private in the
    // catalog module; the service stores the ingredient under that key, so the two must agree.
    String key = fold(cleaned);
    boolean listed = suggestions.stream().anyMatch(s -> fold(s.name()).equals(key));
    return new IngredientOptions(suggestions, listed ? null : cleaned);
  }

  /**
   * Whether the list has no row at all, which the picker words as "nothing to suggest".
   *
   * @return {@code true} if there is neither a suggestion nor an add row
   */
  public boolean empty() {
    return suggestions.isEmpty() && addName == null;
  }

  private static String fold(String name) {
    String decomposed =
        Normalizer.normalize(name.strip().replaceAll("\\s+", " "), Normalizer.Form.NFD);
    return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }
}
