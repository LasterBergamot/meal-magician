package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealView;
import java.util.List;

/**
 * What the occasion's pick sheet prints: the library meals that can go on the menu, and the "Create
 * and add" form. It is the occasion twin of {@link PickPage}, without a day or slot type.
 *
 * @param occasionId the occasion the meal will be added to
 * @param query the search text as typed, stripped
 * @param meals at most {@link #LIMIT} library meals, recently planned first, then by name
 * @param more whether the library holds more matching meals than are listed
 * @param libraryEmpty whether the library has no meal at all (no search text and no match)
 * @param quick the state of the quick-add form
 */
public record OccasionPickPage(
    long occasionId,
    String query,
    List<MealView> meals,
    boolean more,
    boolean libraryEmpty,
    PickQuick quick) {

  /** How many meals the sheet lists at most; a search narrows the rest. */
  public static final int LIMIT = 20;

  /**
   * The address of the sheet's content, which the search box refetches.
   *
   * @return for example {@code /occasions/7/pick}
   */
  public String pickHref() {
    return "/occasions/" + occasionId + "/pick";
  }

  /**
   * The address a library meal's button posts to.
   *
   * @return for example {@code /occasions/7/menu}
   */
  public String menuAction() {
    return "/occasions/" + occasionId + "/menu";
  }

  /**
   * The address the "Create and add" form posts to.
   *
   * @return for example {@code /occasions/7/menu/quick}
   */
  public String quickAction() {
    return menuAction() + "/quick";
  }

  /**
   * The address of the occasion's page, for the "back" link of the no-JavaScript page.
   *
   * @return for example {@code /occasions/7}
   */
  public String backHref() {
    return "/occasions/" + occasionId;
  }
}
