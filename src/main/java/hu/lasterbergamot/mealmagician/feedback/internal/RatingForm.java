package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.RatingInput;
import hu.lasterbergamot.mealmagician.feedback.RatingView;
import hu.lasterbergamot.mealmagician.feedback.Thumb;

/**
 * What the rating form shows and what it posts, as plain text. It keeps the raw strings so a
 * rejected form can print back exactly what the person typed, even a stars value that is not a
 * number.
 *
 * <p>Parsing is lenient on purpose: a hand-edited value must become a field error (HTTP 422, via
 * the service's own rules), never a 500. A thumb that is not {@code UP} or {@code DOWN} parses to
 * {@code null}, which the service refuses as "thumb required". Stars that are not a whole number
 * parse to {@code 0}, which the service refuses as "out of range".
 *
 * @param thumb {@code UP}, {@code DOWN}, or empty for none chosen
 * @param stars {@code 1} to {@code 5}, or empty for no stars
 * @param comment the comment text, never {@code null}
 */
public record RatingForm(String thumb, String stars, String comment) {

  /** The stars value that stands for "refused": the service rejects anything outside 1 to 5. */
  static final int UNPARSEABLE_STARS = 0;

  /**
   * An empty form, for a slot the member has not rated.
   *
   * @return a form with nothing chosen and no comment
   */
  public static RatingForm empty() {
    return new RatingForm("", "", "");
  }

  /**
   * A form pre-filled with the member's saved rating.
   *
   * @param rating the member's own rating
   * @return a form holding its thumb, stars and comment
   */
  public static RatingForm of(RatingView rating) {
    return new RatingForm(
        rating.thumb().name(),
        rating.stars() == null ? "" : rating.stars().toString(),
        rating.comment() == null ? "" : rating.comment());
  }

  /**
   * Takes the posted parameters as they are, replacing a missing one with an empty string so the
   * template never prints {@code null}.
   *
   * @param thumb the posted {@code thumb}, possibly {@code null}
   * @param stars the posted {@code stars}, possibly {@code null}
   * @param comment the posted {@code comment}, possibly {@code null}
   * @return the form as typed
   */
  public static RatingForm parse(String thumb, String stars, String comment) {
    return new RatingForm(
        thumb == null ? "" : thumb, stars == null ? "" : stars, comment == null ? "" : comment);
  }

  /**
   * The input to hand to the rating service.
   *
   * @return the thumb ({@code null} when it is neither {@code UP} nor {@code DOWN}), the stars
   *     ({@code null} when blank, {@code 0} when not a whole number) and the comment
   */
  public RatingInput toInput() {
    return new RatingInput(parsedThumb(), parsedStars(), comment);
  }

  /**
   * Tells whether thumb up is the chosen radio.
   *
   * @return {@code true} for {@code UP}
   */
  public boolean up() {
    return parsedThumb() == Thumb.UP;
  }

  /**
   * Tells whether thumb down is the chosen radio.
   *
   * @return {@code true} for {@code DOWN}
   */
  public boolean down() {
    return parsedThumb() == Thumb.DOWN;
  }

  /**
   * Tells whether "no stars" is the chosen radio.
   *
   * @return {@code true} when the stars value is blank
   */
  public boolean noStars() {
    return stars.isBlank();
  }

  /**
   * Tells whether a number of stars is the chosen radio.
   *
   * @param count 1 to 5
   * @return {@code true} when the form holds exactly that number
   */
  public boolean hasStars(int count) {
    return stars.strip().equals(Integer.toString(count));
  }

  private Thumb parsedThumb() {
    return switch (thumb) {
      case "UP" -> Thumb.UP;
      case "DOWN" -> Thumb.DOWN;
      default -> null;
    };
  }

  private Integer parsedStars() {
    if (stars.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(stars.strip());
    } catch (NumberFormatException notANumber) {
      return UNPARSEABLE_STARS;
    }
  }
}
