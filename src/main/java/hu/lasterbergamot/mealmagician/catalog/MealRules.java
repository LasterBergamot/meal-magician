package hu.lasterbergamot.mealmagician.catalog;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The input rules for meals, recipe links and tags, in one place so the service, its tests and
 * every future page agree on them. Each method returns the cleaned value or throws {@link
 * InvalidMealException}; none of them touches the database.
 */
final class MealRules {

  /** The longest meal name. */
  static final int NAME_MAX = 200;

  /** The longest notes text. */
  static final int NOTES_MAX = 2000;

  /** The longest recipe link. */
  static final int RECIPE_URL_MAX = 2000;

  /** The longest tag name. */
  static final int TAG_MAX = 50;

  private MealRules() {}

  /**
   * Cleans a meal name.
   *
   * @param raw the typed name, may be {@code null}
   * @return the name without surrounding blanks
   * @throws InvalidMealException with {@code NAME_REQUIRED} if it is empty, or {@code
   *     NAME_TOO_LONG} if it exceeds {@link #NAME_MAX}
   */
  static String name(String raw) {
    String cleaned = raw == null ? "" : raw.strip();
    if (cleaned.isEmpty()) {
      throw new InvalidMealException(MealProblem.NAME_REQUIRED);
    }
    if (cleaned.length() > NAME_MAX) {
      throw new InvalidMealException(MealProblem.NAME_TOO_LONG);
    }
    return cleaned;
  }

  /**
   * Cleans the notes. Blank notes are stored as "no notes" ({@code null}), so the database does not
   * distinguish "" from "never filled in".
   *
   * @param raw the typed notes, may be {@code null}
   * @return the trimmed notes, or {@code null} for none
   * @throws InvalidMealException with {@code NOTES_TOO_LONG} if they exceed {@link #NOTES_MAX}
   */
  static String notes(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String cleaned = raw.strip();
    if (cleaned.length() > NOTES_MAX) {
      throw new InvalidMealException(MealProblem.NOTES_TOO_LONG);
    }
    return cleaned;
  }

  /**
   * Cleans the recipe link. Only {@code http} and {@code https} are accepted: the link is shown as
   * a clickable anchor, and a {@code javascript:} or {@code data:} link there would run code.
   *
   * @param raw the typed link, may be {@code null}
   * @return the trimmed link, or {@code null} for none
   * @throws InvalidMealException with {@code RECIPE_URL_INVALID} if it is not a web address or is
   *     too long
   */
  static String recipeUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String cleaned = raw.strip();
    if (cleaned.length() > RECIPE_URL_MAX || !isWebAddress(cleaned)) {
      throw new InvalidMealException(MealProblem.RECIPE_URL_INVALID);
    }
    return cleaned;
  }

  /**
   * Cleans a list of tag names into the tags a meal should carry. Surrounding blanks are removed
   * and inner runs of blanks collapsed, so " Quick soup " is "Quick soup". Names that differ only
   * in letter case are one tag: the first spelling is kept.
   *
   * @param raw the typed tag names, may be {@code null} or contain repeats
   * @return the tag names keyed by their lower-case form (see {@link #tagKey}), in the order typed;
   *     empty for {@code null}
   * @throws InvalidMealException with {@code TAG_INVALID} if a name is blank or longer than {@link
   *     #TAG_MAX}
   */
  static Map<String, String> tags(Collection<String> raw) {
    Map<String, String> byKey = new LinkedHashMap<>();
    if (raw == null) {
      return byKey;
    }
    for (String item : raw) {
      String display = tagName(item);
      byKey.putIfAbsent(tagKey(display), display);
    }
    return byKey;
  }

  /**
   * Cleans one tag name for storing and showing it.
   *
   * @param raw the typed name, may be {@code null}
   * @return the name with blanks trimmed and collapsed
   * @throws InvalidMealException with {@code TAG_INVALID} if it is empty or too long
   */
  static String tagName(String raw) {
    String cleaned = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    if (cleaned.isEmpty() || cleaned.length() > TAG_MAX) {
      throw new InvalidMealException(MealProblem.TAG_INVALID);
    }
    return cleaned;
  }

  /**
   * The key that identifies a tag regardless of spelling: the cleaned name in lower case. Search
   * uses it too, so "VEGAN" in the filter finds the tag stored as "Vegan". It does not validate: a
   * name that cannot exist simply finds nothing.
   *
   * @param name a tag name, not {@code null}
   * @return the lower-case, cleaned name
   */
  static String tagKey(String name) {
    return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /**
   * Turns the library search text into a SQL {@code LIKE} pattern. The text is escaped with {@code
   * !} (the escape character the query declares), so {@code %} and {@code _} in what the person
   * typed match only themselves.
   *
   * @param text the search text, may be {@code null}
   * @return the pattern {@code %text%} in lower case, or {@code null} when there is nothing to
   *     search for
   */
  static String namePattern(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String escaped =
        text.strip()
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    return "%" + escaped + "%";
  }

  private static boolean isWebAddress(String text) {
    try {
      URI uri = URI.create(text);
      String scheme = uri.getScheme();
      boolean webScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
      return webScheme && uri.getHost() != null;
    } catch (IllegalArgumentException malformed) {
      return false;
    }
  }
}
