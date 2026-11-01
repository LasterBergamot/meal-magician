package hu.lasterbergamot.mealmagician.catalog;

import java.util.List;

/**
 * What the household types in when it creates or edits a meal, before it is checked. Blanks around
 * the values are fine; the service cleans them and throws {@link InvalidMealException} for anything
 * that breaks a rule.
 *
 * @param name the meal's name, required; at most 200 characters
 * @param notes free text about the meal, optional; at most 2000 characters
 * @param recipeUrl a link to the recipe, optional; must be an {@code http} or {@code https} address
 * @param tags the tag names to put on the meal; {@code null} means none. Names that differ only in
 *     letter case are the same tag, and the first spelling wins.
 */
public record MealDetails(String name, String notes, String recipeUrl, List<String> tags) {}
