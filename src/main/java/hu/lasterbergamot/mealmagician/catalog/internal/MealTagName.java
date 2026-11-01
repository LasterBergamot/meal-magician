package hu.lasterbergamot.mealmagician.catalog.internal;

/**
 * One row of the "which tags does each meal have" query: a meal id with one of its tag names. A
 * projection, so the search can group tags by meal without loading the entities.
 *
 * @param mealId the meal the tag is on
 * @param name the tag's name as typed
 */
public record MealTagName(long mealId, String name) {}
