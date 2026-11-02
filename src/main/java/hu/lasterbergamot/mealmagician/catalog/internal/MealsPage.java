package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.MealView;
import java.util.List;

/**
 * Everything the meal list page and its results fragment need, so the templates take typed data and
 * no entities.
 *
 * @param filter the search, tag, archived switch and sort order in force
 * @param meals the meals to list, already in the order to show them
 * @param tags the tags the household uses, for the filter chips
 * @param quickName the text typed into the quick-add form (empty for a fresh page)
 */
public record MealsPage(
    MealFilter filter, List<MealView> meals, List<String> tags, String quickName) {

  /**
   * Whether the whole library is empty: nothing listed and nothing narrowing the list. Then the
   * page invites the first meal instead of saying that nothing matches.
   *
   * @return {@code true} for a household with no meals at all (archived ones are not counted)
   */
  public boolean libraryEmpty() {
    return meals.isEmpty() && !filter.active();
  }
}
