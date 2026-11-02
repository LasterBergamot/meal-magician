package hu.lasterbergamot.mealmagician.planning.internal;

/**
 * One meal on an occasion template's menu, as the template page prints it. Unlike a {@link
 * MenuLine} it has no item id or place, because a template's menu is read-only.
 *
 * @param mealId the library meal, linked from the page
 * @param name the meal's name, taken from the catalog
 * @param archived whether the meal is archived; the page then shows an Archived badge
 */
public record MealLine(long mealId, String name, boolean archived) {

  /**
   * The address of the meal's page.
   *
   * @return for example {@code /meals/3}
   */
  public String mealHref() {
    return "/meals/" + mealId;
  }
}
