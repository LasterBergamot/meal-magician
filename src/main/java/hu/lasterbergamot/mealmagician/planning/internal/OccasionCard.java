package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;

/**
 * One occasion as the Occasions list shows it: a row, or the gold-soft card for the next one. It
 * holds the words already formatted (the date label), so the template only prints them.
 *
 * @param id the occasion's id, used in its link
 * @param name the trimmed name
 * @param dateLabel the date written out for the reader, for example {@code Monday, December 24,
 *     2026}
 * @param type the kind of occasion; the label comes from {@link #typeKey()}
 * @param mealCount how many meals are on its menu
 * @param today whether the occasion is on the current day (the card then shows a Today badge)
 */
public record OccasionCard(
    long id, String name, String dateLabel, OccasionType type, int mealCount, boolean today) {

  /**
   * The address of the occasion's detail page.
   *
   * @return for example {@code /occasions/7}
   */
  public String href() {
    return "/occasions/" + id;
  }

  /**
   * The message key of the kind's label. The planning pages reuse the catalog's labels, so the
   * words are translated once.
   *
   * @return for example {@code meal.occasion.party}
   */
  public String typeKey() {
    return OccasionTypes.key(type);
  }
}
