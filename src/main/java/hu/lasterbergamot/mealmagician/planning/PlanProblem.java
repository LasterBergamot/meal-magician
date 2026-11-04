package hu.lasterbergamot.mealmagician.planning;

/**
 * The rule a planning input broke. A page chooses its message from this, so the messages stay in
 * the translation bundles and not in the service.
 */
public enum PlanProblem {
  /** The slot note is longer than {@link PlanRules#NOTE_MAX} characters. */
  NOTE_TOO_LONG,
  /** A copy targets the very day or ISO week it copies from. */
  SAME_TARGET,
  /** The day, week or menu to copy or save as a template has nothing in it. */
  NOTHING_TO_COPY,
  /** A template or occasion name is missing or blank. */
  NAME_REQUIRED,
  /** A template or occasion name is longer than {@link PlanRules#NAME_MAX} characters. */
  NAME_TOO_LONG,
  /** Occasion notes are longer than {@link PlanRules#NOTES_MAX} characters. */
  NOTES_TOO_LONG,
  /** An occasion has no date. */
  DATE_REQUIRED,
  /** An occasion has no type. */
  TYPE_REQUIRED,
  /** A draft slot lies outside the ISO week that is being written. */
  OUTSIDE_WEEK,
  /** A draft slot names an archived meal, which the library no longer offers. */
  MEAL_ARCHIVED
}
