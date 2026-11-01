package hu.lasterbergamot.mealmagician.catalog;

/**
 * A meal was created, edited, archived or restored. Other modules listen for it instead of calling
 * the catalog, so the catalog does not need to know who cares: the assistant re-indexes the meal
 * for its search, and the planner may refresh what it shows.
 *
 * <p>Published inside the same transaction as the change. Listeners that must only see committed
 * data should use {@code @ApplicationModuleListener}, which runs after the commit.
 *
 * @param householdId the household the meal belongs to
 * @param mealId the meal that changed
 */
public record MealChanged(long householdId, long mealId) {}
