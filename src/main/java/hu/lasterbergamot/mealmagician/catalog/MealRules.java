package hu.lasterbergamot.mealmagician.catalog;

import java.net.URI;
import java.text.Normalizer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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

  /** The longest ingredient name. */
  static final int INGREDIENT_MAX = 60;

  /** The most ingredients one meal can have. */
  static final int INGREDIENTS_PER_MEAL = 30;

  /** The combining accent marks that {@link Normalizer} splits off a letter. */
  private static final Pattern MARKS = Pattern.compile("\\p{M}+");

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
   * Cleans the occasion types a meal suits into a set in enum order. Repeats and {@code null}
   * entries are dropped rather than refused: the types come from checkboxes, so there is nothing
   * the person could have typed wrong.
   *
   * @param raw the wanted types, may be {@code null}
   * @return the distinct types in the order of the {@link OccasionType} constants; empty for {@code
   *     null}
   */
  static Set<OccasionType> occasionTypes(Collection<OccasionType> raw) {
    EnumSet<OccasionType> types = EnumSet.noneOf(OccasionType.class);
    if (raw != null) {
      raw.stream().filter(Objects::nonNull).forEach(types::add);
    }
    return types;
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
   * The key the Tags field's suggestions match on: like {@link #tagKey} but with accents removed
   * too, so typing "vegan" finds the tag "Vegán". It is only for searching. A tag's identity (the
   * stored {@code name_key}) stays {@link #tagKey}, which keeps accents, so "vegan" and "vegán"
   * remain two tags and picking a suggestion is how the person avoids a near-duplicate.
   *
   * @param text a tag name or the text typed so far, not {@code null}
   * @return the accent-free, lower-case, cleaned text
   */
  static String tagSearchKey(String text) {
    return ingredientKey(text);
  }

  /**
   * Cleans a list of ingredient names into the ingredients a meal should carry. Like {@link #tags}
   * but the identity ignores accents too: "Tojás", "tojas" and " TOJÁS " are one ingredient and the
   * first spelling is kept.
   *
   * @param raw the typed ingredient names, may be {@code null} or contain repeats
   * @return the names keyed by their folded form (see {@link #ingredientKey}), in the order typed;
   *     empty for {@code null}
   * @throws InvalidMealException with {@code INGREDIENT_INVALID} if a name is blank or longer than
   *     {@link #INGREDIENT_MAX}, or if there are more than {@link #INGREDIENTS_PER_MEAL} different
   *     ones
   */
  static Map<String, String> ingredients(Collection<String> raw) {
    Map<String, String> byKey = new LinkedHashMap<>();
    if (raw == null) {
      return byKey;
    }
    for (String item : raw) {
      String display = ingredientName(item);
      byKey.putIfAbsent(ingredientKey(display), display);
    }
    if (byKey.size() > INGREDIENTS_PER_MEAL) {
      throw new InvalidMealException(MealProblem.INGREDIENT_INVALID);
    }
    return byKey;
  }

  /**
   * Cleans one ingredient name for storing and showing it.
   *
   * @param raw the typed name, may be {@code null}
   * @return the name with blanks trimmed and collapsed
   * @throws InvalidMealException with {@code INGREDIENT_INVALID} if it is empty or too long
   */
  static String ingredientName(String raw) {
    String cleaned = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    if (cleaned.isEmpty() || cleaned.length() > INGREDIENT_MAX) {
      throw new InvalidMealException(MealProblem.INGREDIENT_INVALID);
    }
    return cleaned;
  }

  /**
   * The key that identifies an ingredient regardless of spelling: the cleaned name with accents
   * removed and in lower case, so "Őszibarack" is {@code oszibarack}. Search and the picker use it
   * too, so typing "tojas" finds "tojás". It does not validate: a name that cannot exist simply
   * finds nothing.
   *
   * @param name an ingredient name or search text, not {@code null}
   * @return the accent-free, lower-case, cleaned name
   */
  static String ingredientKey(String name) {
    String decomposed =
        Normalizer.normalize(name.strip().replaceAll("\\s+", " "), Normalizer.Form.NFD);
    return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }

  /**
   * Turns a folded key into a SQL {@code LIKE} pattern that matches it anywhere in a key column,
   * escaped with {@code !} like {@link #namePattern}.
   *
   * @param key a key from {@link #ingredientKey}
   * @return {@code %key%}
   */
  static String keyPattern(String key) {
    return "%" + escapeLike(key) + "%";
  }

  /**
   * Like {@link #keyPattern}, but matching only at the start of a key column.
   *
   * @param key a key from {@link #ingredientKey}
   * @return {@code key%}
   */
  static String keyPrefixPattern(String key) {
    return escapeLike(key) + "%";
  }

  private static String escapeLike(String text) {
    return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
    return keyPattern(text.strip().toLowerCase(Locale.ROOT));
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
