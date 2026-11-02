package hu.lasterbergamot.mealmagician.planning;

/**
 * How much of the plan a plan template covers. The constant names are stored in {@code
 * plan_template.scope} and limited by a {@code CHECK} in migration V9, so a new scope needs a
 * migration too.
 */
public enum TemplateScope {
  /** One day. Applying it fills the day the person picks. */
  DAY,
  /** One ISO week, Monday to Sunday. Applying it fills the week of the day the person picks. */
  WEEK
}
