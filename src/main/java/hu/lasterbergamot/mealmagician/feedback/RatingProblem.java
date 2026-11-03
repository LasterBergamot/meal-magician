package hu.lasterbergamot.mealmagician.feedback;

/**
 * The rule a rating input broke. A page chooses its message from this, so the messages stay in the
 * translation bundles and not in the service.
 */
public enum RatingProblem {
  /** No thumb was chosen. */
  THUMB_REQUIRED,
  /** The stars are not between 1 and 5. */
  STARS_OUT_OF_RANGE,
  /** The comment is longer than {@link RatingRules#MAX_COMMENT} characters. */
  COMMENT_TOO_LONG,
  /** The slot is dated after today, so the meal has not been eaten yet. */
  NOT_YET
}
