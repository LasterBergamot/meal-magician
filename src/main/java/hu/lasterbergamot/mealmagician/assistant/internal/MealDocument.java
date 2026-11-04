package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a meal into the one piece of text that is embedded, and fingerprints it. Pure functions: no
 * database, no clock, no model, so the same meal always gives the same text.
 *
 * <p><strong>What is in the document.</strong> The name, tags, ingredients, occasion types and
 * notes: everything that says <em>what the meal is</em>. <strong>Ratings are deliberately left
 * out</strong>: they change whenever someone rates, and the document (and so its hash) must change
 * only when the meal itself does, or every rating would cost an embedding call. The recipe link,
 * the archive flag and the version are left out too, because they say nothing about the food.
 *
 * <p>The text is data that members typed. It is sent to the embedding model and never logged.
 */
public final class MealDocument {

  /** The longest document, in characters. Notes are cut first because they come last. */
  public static final int MAX_CHARS = 2_000;

  private MealDocument() {}

  /**
   * Builds the text to embed for a meal. Lines are {@code Name: …}, {@code Tags: a, b}, {@code
   * Ingredients: x, y}, {@code Occasions: BIRTHDAY, …} and {@code Notes: …}; a line with nothing to
   * say is omitted. Whitespace inside the notes (including line breaks) is collapsed to single
   * spaces, so a note cannot imitate a field line and trivial edits do not change the hash.
   *
   * @param meal the meal as the catalog module returns it
   * @return the document, at most {@value #MAX_CHARS} characters; stable for the same meal
   */
  public static String of(MealView meal) {
    List<String> lines = new ArrayList<>();
    lines.add("Name: " + meal.name());
    addList(lines, "Tags", meal.tags());
    addList(lines, "Ingredients", meal.ingredients());
    addList(lines, "Occasions", meal.occasionTypes().stream().map(OccasionType::name).toList());
    if (meal.notes() != null && !meal.notes().isBlank()) {
      lines.add("Notes: " + meal.notes().strip().replaceAll("\\s+", " "));
    }
    return cap(String.join("\n", lines));
  }

  /**
   * Fingerprints a document together with the model that embeds it, so a changed model makes every
   * stored hash stale and everything is embedded again.
   *
   * @param text the document, or any text
   * @param model the model key (the model id and the vector size)
   * @return 64 lower-case hex characters: SHA-256 of {@code model + "\n" + text}
   */
  public static String hash(String text, String model) {
    return TokenCodes.hash(model + "\n" + text);
  }

  /**
   * Shortens a text to {@value #MAX_CHARS} characters without splitting an emoji or another
   * character outside the basic plane in half.
   *
   * @param text any text
   * @return the text itself when short enough, otherwise its first characters
   */
  public static String cap(String text) {
    if (text.length() <= MAX_CHARS) {
      return text;
    }
    int end = MAX_CHARS;
    if (Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(0, end);
  }

  private static void addList(List<String> lines, String label, List<String> values) {
    if (!values.isEmpty()) {
      lines.add(label + ": " + String.join(", ", values));
    }
  }
}
