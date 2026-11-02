package hu.lasterbergamot.mealmagician.planning.internal;

/**
 * One meal on an occasion's menu as the detail page shows it, with its place so the page can know
 * whether a move up or down is possible. The read-only menu of #112 only draws them; #113 adds the
 * move and remove actions that use the last four methods.
 *
 * @param itemId the menu item's id, used by the actions of #113
 * @param mealId the library meal, linked from the menu
 * @param mealName the meal's name, taken from the catalog
 * @param archived whether the meal is archived; the menu then shows an Archived badge
 * @param position the place on the menu, counted from 0
 * @param size how many meals the menu has, so the last place is {@code size - 1}
 */
public record MenuLine(
    long itemId, long mealId, String mealName, boolean archived, int position, int size) {

  /**
   * The address of the meal's page.
   *
   * @return for example {@code /meals/3}
   */
  public String mealHref() {
    return "/meals/" + mealId;
  }

  /**
   * Whether the meal can move one place up, which is false for the first place.
   *
   * @return {@code true} if it is not first
   */
  public boolean canMoveUp() {
    return position > 0;
  }

  /**
   * Whether the meal can move one place down, which is false for the last place.
   *
   * @return {@code true} if it is not last
   */
  public boolean canMoveDown() {
    return position < size - 1;
  }

  /**
   * The place one up, to send with a move action.
   *
   * @return {@code position - 1}, which is only meaningful when {@link #canMoveUp()}
   */
  public int upPosition() {
    return position - 1;
  }

  /**
   * The place one down, to send with a move action.
   *
   * @return {@code position + 1}, which is only meaningful when {@link #canMoveDown()}
   */
  public int downPosition() {
    return position + 1;
  }
}
