package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;

/**
 * One occasion (a birthday, Christmas) as the strip above the week's days shows it: a link to the
 * occasion's own page.
 *
 * @param id the occasion's id
 * @param name the occasion's name
 * @param dayLabel the translated weekday and date, for example {@code Szerda nov. 4.}
 * @param type the kind of occasion, shown as a badge
 */
public record OccasionChip(long id, String name, String dayLabel, OccasionType type) {

  /**
   * The address of the occasion's page.
   *
   * @return for example {@code /occasions/7}
   */
  public String href() {
    return "/occasions/" + id;
  }

  /**
   * The message key of the type's label.
   *
   * @return for example {@code meal.occasion.birthday}
   */
  public String typeKey() {
    return OccasionTypes.key(type);
  }
}
