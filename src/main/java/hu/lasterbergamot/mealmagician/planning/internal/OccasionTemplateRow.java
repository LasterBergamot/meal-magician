package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;

/**
 * One occasion template as a row of the Templates section on the Occasions list.
 *
 * @param id the template's id, used in its link
 * @param name the template's name
 * @param type the kind of occasion it makes; the label comes from {@link #typeKey()}
 * @param mealCount how many meals its menu has
 */
public record OccasionTemplateRow(long id, String name, OccasionType type, int mealCount) {

  /**
   * The address of the template's page.
   *
   * @return for example {@code /occasions/templates/4}
   */
  public String href() {
    return "/occasions/templates/" + id;
  }

  /**
   * The message key of the kind's label.
   *
   * @return for example {@code meal.occasion.holiday}
   */
  public String typeKey() {
    return OccasionTypes.key(type);
  }
}
