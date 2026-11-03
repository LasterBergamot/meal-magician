package hu.lasterbergamot.mealmagician.catalog.internal;

/**
 * One row of the "which ingredients does each meal have" query: a meal id with one of its
 * ingredient names. A projection, so the search can group ingredients by meal without loading the
 * entities.
 *
 * @param mealId the meal the ingredient is on
 * @param name the ingredient's name as typed first
 */
public record MealIngredientName(long mealId, String name) {}
