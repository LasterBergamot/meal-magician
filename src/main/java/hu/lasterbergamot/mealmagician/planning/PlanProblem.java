package hu.lasterbergamot.mealmagician.planning;

/**
 * The rule a planning input broke. A page chooses its message from this, so the messages stay in
 * the translation bundles and not in the service.
 */
public enum PlanProblem {
  /** The slot note is longer than {@link PlanRules#NOTE_MAX} characters. */
  NOTE_TOO_LONG
}
