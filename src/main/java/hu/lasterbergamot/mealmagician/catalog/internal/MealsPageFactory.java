package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.RecentMealsProvider;
import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link MealsPage}: searches the library through {@link MealService} and, for the
 * default sort, puts the recently planned meals first.
 *
 * <p>The "recently used" order comes from the planner, which the catalog cannot call directly
 * (planning depends on catalog; the other way round would be a module cycle). The catalog declares
 * {@link RecentMealsProvider} and planning implements it, so this class asks the interface. The
 * provider is looked up lazily and may be missing (a catalog-only test, say): then the recent sort
 * simply has no recently used meals and lists the newest additions first.
 */
@Component
class MealsPageFactory {

  /** How many recently planned meals are asked for; a household's library is far smaller. */
  static final int RECENT_LIMIT = 500;

  private final MealService meals;
  private final ObjectProvider<RecentMealsProvider> recents;

  MealsPageFactory(MealService meals, ObjectProvider<RecentMealsProvider> recents) {
    this.meals = meals;
    this.recents = recents;
  }

  /**
   * Builds the list page for one filter.
   *
   * @param userId the signed-in person's id
   * @param householdId the household whose library is listed
   * @param filter the filter and sort order from the request
   * @param quickName the text to keep in the quick-add form
   * @return the page model
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  MealsPage page(long userId, long householdId, MealFilter filter, String quickName) {
    List<MealView> found =
        meals.search(
            userId,
            householdId,
            new MealSearch(filter.q(), filter.tag(), filter.ingredient(), filter.archived()));
    List<MealView> ordered =
        MealFilter.NAME.equals(filter.sort()) ? found : recentFirst(userId, householdId, found);
    return new MealsPage(
        filter,
        ordered,
        meals.tagNames(userId, householdId),
        ingredientNames(userId, householdId, filter, found),
        quickName);
  }

  /**
   * The ingredient names for the filter chips: every ingredient on a library meal, so the row does
   * not shrink to the one chosen ingredient once a filter is on. {@code MealService} has no call
   * for "the ingredients in use", so they are read off the meals; when a filter narrowed the
   * result, the unfiltered library is searched once more (a household's library is small).
   *
   * @return the distinct names in Hungarian collation order
   */
  private List<String> ingredientNames(
      long userId, long householdId, MealFilter filter, List<MealView> found) {
    List<MealView> library =
        filter.narrows()
            ? meals.search(userId, householdId, new MealSearch(null, null, null, filter.archived()))
            : found;
    Collator collator = Collator.getInstance(Locale.of("hu"));
    return library.stream()
        .flatMap(meal -> meal.ingredients().stream())
        .distinct()
        .sorted(collator)
        .toList();
  }

  /**
   * Orders meals for the "recent" sort: the recently planned ones in the order they were planned,
   * then every other meal, newest first. A meal that was just added is therefore never lost at the
   * bottom of the list.
   *
   * @param found the meals to order
   * @return a new list, in the order to show
   */
  private List<MealView> recentFirst(long userId, long householdId, List<MealView> found) {
    RecentMealsProvider provider = recents.getIfAvailable();
    List<Long> recent =
        provider == null ? List.of() : provider.recentMealIds(userId, householdId, RECENT_LIMIT);
    Map<Long, Integer> rank = new HashMap<>();
    for (int i = 0; i < recent.size(); i++) {
      rank.putIfAbsent(recent.get(i), i);
    }
    return found.stream()
        .sorted(
            Comparator.comparingInt(
                    (MealView meal) -> rank.getOrDefault(meal.id(), Integer.MAX_VALUE))
                .thenComparing(Comparator.comparingLong(MealView::id).reversed()))
        .toList();
  }
}
