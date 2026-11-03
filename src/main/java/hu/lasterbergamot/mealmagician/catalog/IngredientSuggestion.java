package hu.lasterbergamot.mealmagician.catalog;

/**
 * One line of the ingredient picker's suggestions.
 *
 * @param name the ingredient's name as it should be shown and, when picked, saved
 * @param known {@code true} if the household already has this ingredient, {@code false} if it comes
 *     from the starter list and would be created for the household when a meal uses it
 */
public record IngredientSuggestion(String name, boolean known) {}
