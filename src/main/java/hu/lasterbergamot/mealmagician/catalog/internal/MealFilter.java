package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What the meal list is currently filtered and sorted by: the search text, the chosen tag, the
 * chosen ingredient, whether archived meals are shown and the sort order. It is the whole state of
 * the list page, kept in the URL ({@code /meals?q=soup&tag=vegan&sort=name}) so a reload, Back and
 * a shared link all show the same list.
 *
 * <p>The chips and segments on the page link to other filters, built with {@link #href}, so every
 * link keeps the parts of the state it does not change.
 *
 * @param q the search text; never {@code null}, blank for none
 * @param tag the chosen tag; never {@code null}, blank for none
 * @param ingredient the chosen ingredient; never {@code null}, blank for none
 * @param archived whether archived meals are listed too
 * @param sort {@link #RECENT} or {@link #NAME}
 */
public record MealFilter(String q, String tag, String ingredient, boolean archived, String sort) {

  /** Recently planned meals first, then the newest additions. The default. */
  public static final String RECENT = "recent";

  /** Alphabetical order. */
  public static final String NAME = "name";

  /**
   * Builds a filter from the raw request parameters, tolerating anything a hand-edited URL can
   * hold.
   *
   * @param q the search text, may be {@code null}
   * @param tag the tag, may be {@code null}
   * @param ingredient the ingredient, may be {@code null}
   * @param archived whether archived meals are wanted
   * @param sort the sort order; anything but {@code name} means {@link #RECENT}
   * @return a filter with trimmed text and a known sort
   */
  public static MealFilter of(
      String q, String tag, String ingredient, boolean archived, String sort) {
    return new MealFilter(
        q == null ? "" : q.strip(),
        tag == null ? "" : tag.strip(),
        ingredient == null ? "" : ingredient.strip(),
        archived,
        NAME.equals(sort) ? NAME : RECENT);
  }

  /**
   * Builds a filter without an ingredient, the form used before ingredients existed.
   *
   * @param q the search text, may be {@code null}
   * @param tag the tag, may be {@code null}
   * @param archived whether archived meals are wanted
   * @param sort the sort order; anything but {@code name} means {@link #RECENT}
   * @return a filter with trimmed text and a known sort
   */
  public static MealFilter of(String q, String tag, boolean archived, String sort) {
    return of(q, tag, null, archived, sort);
  }

  /**
   * Whether the search text, a tag or an ingredient narrows the list (the archived switch does
   * not). The chip rows list what the household uses, and for that they need the unfiltered meals
   * whenever this is true.
   *
   * @return {@code true} if the text, the tag or the ingredient is set
   */
  public boolean narrows() {
    return !q.isBlank() || !tag.isBlank() || !ingredient.isBlank();
  }

  /**
   * Whether anything narrows the list. An empty result with no filter means an empty library; an
   * empty result with a filter means "nothing matches".
   *
   * @return {@code true} if there is search text, a tag, an ingredient or the archived switch is on
   */
  public boolean active() {
    return narrows() || archived;
  }

  /**
   * The address of the same list with one part changed.
   *
   * @param newTag the tag to filter by; blank for none
   * @param newIngredient the ingredient to filter by; blank for none
   * @param newArchived whether archived meals are shown
   * @param newSort {@link #RECENT} or {@link #NAME}
   * @return a {@code /meals?...} address with the search text kept and defaults left out
   */
  public String href(String newTag, String newIngredient, boolean newArchived, String newSort) {
    UriComponentsBuilder url = UriComponentsBuilder.fromPath("/meals");
    if (!q.isBlank()) {
      url.queryParam("q", "{q}");
    }
    if (!newTag.isBlank()) {
      url.queryParam("tag", "{tag}");
    }
    if (!newIngredient.isBlank()) {
      url.queryParam("ingredient", "{ingredient}");
    }
    if (newArchived) {
      url.queryParam("archived", "true");
    }
    if (NAME.equals(newSort)) {
      url.queryParam("sort", NAME);
    }
    return url.encode()
        .buildAndExpand(Map.of("q", q, "tag", newTag, "ingredient", newIngredient))
        .toUriString();
  }

  /**
   * The address of this list.
   *
   * @return the same as {@link #href} with this filter's own values
   */
  public String href() {
    return href(tag, ingredient, archived, sort);
  }

  /**
   * The address of this list with the tag switched: choosing the active tag again clears it.
   *
   * @param chosen the tag whose chip was pressed
   * @return the address to fetch
   */
  public String hrefToggleTag(String chosen) {
    return href(chosen.equalsIgnoreCase(tag) ? "" : chosen, ingredient, archived, sort);
  }

  /**
   * The address of this list with the ingredient switched: choosing the active ingredient again
   * clears it.
   *
   * @param chosen the ingredient whose chip was pressed
   * @return the address to fetch
   */
  public String hrefToggleIngredient(String chosen) {
    return href(tag, chosen.equalsIgnoreCase(ingredient) ? "" : chosen, archived, sort);
  }

  /**
   * The address of this list with the archived switch flipped.
   *
   * @return the address to fetch
   */
  public String hrefToggleArchived() {
    return href(tag, ingredient, !archived, sort);
  }

  /**
   * The address of this list with another sort order.
   *
   * @param newSort {@link #RECENT} or {@link #NAME}
   * @return the address to fetch
   */
  public String hrefSort(String newSort) {
    return href(tag, ingredient, archived, newSort);
  }

  /**
   * The address that drops every filter and keeps nothing.
   *
   * @return {@code /meals}
   */
  public String hrefClear() {
    return "/meals";
  }
}
