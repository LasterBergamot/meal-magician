package hu.lasterbergamot.mealmagician.feedback;

/**
 * The input rules for ratings, in one place so the service and its tests agree. It is a pure
 * function: it does not touch the database or the clock (the "not yet" rule needs the slot's date
 * and lives in {@link RatingService}).
 */
public final class RatingRules {

  /** The longest comment, in characters; the column is {@code VARCHAR(500)} too. */
  public static final int MAX_COMMENT = 500;

  private RatingRules() {}

  /**
   * Validates a rating and returns it cleaned: the comment is trimmed, and a blank comment becomes
   * {@code null} so the database does not distinguish "" from "never typed".
   *
   * @param input what the member typed, not {@code null}
   * @return the same thumb and stars with the cleaned comment
   * @throws InvalidRatingException with {@code THUMB_REQUIRED} if there is no thumb, {@code
   *     STARS_OUT_OF_RANGE} if stars are given but not 1 to 5, or {@code COMMENT_TOO_LONG} if the
   *     trimmed comment is over {@link #MAX_COMMENT} characters
   */
  public static RatingInput check(RatingInput input) {
    if (input.thumb() == null) {
      throw new InvalidRatingException(RatingProblem.THUMB_REQUIRED);
    }
    if (input.stars() != null && (input.stars() < 1 || input.stars() > 5)) {
      throw new InvalidRatingException(RatingProblem.STARS_OUT_OF_RANGE);
    }
    String comment = input.comment();
    String cleaned = comment == null || comment.isBlank() ? null : comment.strip();
    if (cleaned != null && cleaned.length() > MAX_COMMENT) {
      throw new InvalidRatingException(RatingProblem.COMMENT_TOO_LONG);
    }
    return new RatingInput(input.thumb(), input.stars(), cleaned);
  }
}
