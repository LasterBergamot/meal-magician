package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.PlanProblem;

/**
 * The state of the "Save as a template" form in the reuse sheet: the name typed so far and the
 * message key of the rejection, if any. A rejected post returns the form with both filled in, so
 * nothing the person typed is lost.
 *
 * @param name the typed name; empty for a fresh form
 * @param error the message key of the problem shown under the field, or {@code null}
 */
public record ReuseSave(String name, String error) {

  /**
   * A fresh form: no name, no error.
   *
   * @return the empty state
   */
  public static ReuseSave empty() {
    return new ReuseSave("", null);
  }

  /**
   * The message key for a refused save.
   *
   * @param problem what the planner refused
   * @return the key of the name problem, or {@code plan.reuse.nothing} when the day or week has no
   *     meals to save
   */
  public static String errorKey(PlanProblem problem) {
    return switch (problem) {
      case NAME_REQUIRED -> "plan.reuse.save.name.required";
      case NAME_TOO_LONG -> "plan.reuse.save.name.long";
      default -> "plan.reuse.nothing";
    };
  }
}
