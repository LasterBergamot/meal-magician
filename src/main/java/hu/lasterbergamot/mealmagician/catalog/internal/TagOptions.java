package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;

/**
 * What the Tags field's suggestion list shows for one search: the tag names to offer, most used
 * first. It is a record of its own (and not a bare list) so the template has one named model, as
 * the ingredient picker's {@code IngredientOptions} does.
 *
 * <p>There is no "Add" row and no "nothing to suggest" row, unlike the ingredient picker: the Tags
 * field is free text, so a name that is not listed is simply typed, and an empty list hides itself.
 *
 * @param names the household's matching tag names, each in its stored spelling; never {@code null}
 */
public record TagOptions(List<String> names) {}
