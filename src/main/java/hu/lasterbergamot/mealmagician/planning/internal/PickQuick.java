package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealProblem;

/**
 * The state of the "Create and add" form in the pick sheet: the name typed so far and the message
 * key of the rejection, if any. A rejected post returns the form with both filled in, so nothing
 * the person typed is lost.
 *
 * @param name the typed name; empty for a fresh form
 * @param error the message key of the problem shown under the field, or {@code null}
 */
public record PickQuick(String name, String error) {

  /**
   * A fresh form: no name, no error.
   *
   * @return the empty state
   */
  public static PickQuick empty() {
    return new PickQuick("", null);
  }

  /**
   * The message key for a rejected name.
   *
   * @param problem what the catalog refused; only the two name problems can happen here
   * @return {@code meal.error.name.long} for a long name, otherwise {@code
   *     meal.error.name.required}
   */
  public static String errorKey(MealProblem problem) {
    return problem == MealProblem.NAME_TOO_LONG
        ? "meal.error.name.long"
        : "meal.error.name.required";
  }
}
