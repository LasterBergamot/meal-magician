package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import java.util.Map;

/**
 * The model of the create/edit form page and fragment: the typed values, the message key shown
 * under each rejected field and whether the form is stale.
 *
 * @param form what the person typed or the stored values
 * @param errors the message key to show under a field, keyed by field name ({@code name}, {@code
 *     notes}, {@code recipeUrl}, {@code tags}, {@code ingredients}); empty for a form without
 *     errors
 * @param conflict whether someone else changed the meal after this form was opened (HTTP 409)
 */
public record MealFormPage(MealForm form, Map<String, String> errors, boolean conflict) {

  /**
   * A form without errors.
   *
   * @param form the values to show
   * @return the page model
   */
  public static MealFormPage of(MealForm form) {
    return new MealFormPage(form, Map.of(), false);
  }

  /**
   * The form drawn again because the service refused a field.
   *
   * @param form what the person typed
   * @param refused the refusal, which says which field and why
   * @return the page model with the message key under the offending field
   */
  public static MealFormPage rejected(MealForm form, InvalidMealException refused) {
    return switch (refused.problem()) {
      case NAME_REQUIRED -> with(form, "name", "meal.error.name.required");
      case NAME_TOO_LONG -> with(form, "name", "meal.error.name.long");
      case NOTES_TOO_LONG -> with(form, "notes", "meal.error.notes.long");
      case RECIPE_URL_INVALID -> with(form, "recipeUrl", "meal.error.url.invalid");
      case TAG_INVALID -> with(form, "tags", "meal.error.tags.invalid");
      case INGREDIENT_INVALID -> with(form, "ingredients", "meal.error.ingredients.invalid");
    };
  }

  /**
   * The form drawn again because it was opened before someone else saved the meal.
   *
   * @param form what the person typed
   * @return the page model with the conflict warning
   */
  public static MealFormPage stale(MealForm form) {
    return new MealFormPage(form, Map.of(), true);
  }

  /**
   * The message key for a field.
   *
   * @param field {@code name}, {@code notes}, {@code recipeUrl}, {@code tags} or {@code
   *     ingredients}
   * @return the key to pass to {@code ctx.t}, or {@code null} if the field has no error
   */
  public String error(String field) {
    return errors.get(field);
  }

  private static MealFormPage with(MealForm form, String field, String key) {
    return new MealFormPage(form, Map.of(field, key), false);
  }
}
