package hu.lasterbergamot.mealmagician.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

class RatingRulesTest {

  private static InvalidRatingException refused(RatingInput input) {
    return catchThrowableOfType(InvalidRatingException.class, () -> RatingRules.check(input));
  }

  @Test
  void aThumbAloneIsAccepted() {
    RatingInput clean = RatingRules.check(new RatingInput(Thumb.UP, null, null));

    assertThat(clean).isEqualTo(new RatingInput(Thumb.UP, null, null));
  }

  @Test
  void aMissingThumbIsRefused() {
    assertThat(refused(new RatingInput(null, 3, "fine")).problem())
        .isEqualTo(RatingProblem.THUMB_REQUIRED);
  }

  @Test
  void starsFromOneToFiveAreAccepted() {
    assertThat(RatingRules.check(new RatingInput(Thumb.UP, 1, null)).stars()).isEqualTo(1);
    assertThat(RatingRules.check(new RatingInput(Thumb.DOWN, 5, null)).stars()).isEqualTo(5);
  }

  @Test
  void zeroStarsAreRefused() {
    assertThat(refused(new RatingInput(Thumb.UP, 0, null)).problem())
        .isEqualTo(RatingProblem.STARS_OUT_OF_RANGE);
  }

  @Test
  void sixStarsAreRefused() {
    assertThat(refused(new RatingInput(Thumb.UP, 6, null)).problem())
        .isEqualTo(RatingProblem.STARS_OUT_OF_RANGE);
  }

  @Test
  void aCommentIsTrimmed() {
    assertThat(RatingRules.check(new RatingInput(Thumb.UP, null, "  tasty \n")).comment())
        .isEqualTo("tasty");
  }

  @Test
  void aBlankCommentBecomesNoComment() {
    assertThat(RatingRules.check(new RatingInput(Thumb.UP, null, "   ")).comment()).isNull();
    assertThat(RatingRules.check(new RatingInput(Thumb.UP, null, "")).comment()).isNull();
  }

  @Test
  void aCommentOfExactlyTheLimitIsAccepted() {
    String atLimit = "x".repeat(RatingRules.MAX_COMMENT);

    assertThat(RatingRules.check(new RatingInput(Thumb.UP, null, atLimit)).comment())
        .isEqualTo(atLimit);
  }

  @Test
  void aCommentOverTheLimitIsRefused() {
    String tooLong = "x".repeat(RatingRules.MAX_COMMENT + 1);

    assertThat(refused(new RatingInput(Thumb.UP, null, tooLong)).problem())
        .isEqualTo(RatingProblem.COMMENT_TOO_LONG);
  }

  @Test
  void spacesAroundTheCommentDoNotCountTowardsTheLimit() {
    String padded = " " + "x".repeat(RatingRules.MAX_COMMENT) + " ";

    assertThat(RatingRules.check(new RatingInput(Thumb.UP, null, padded)).comment())
        .hasSize(RatingRules.MAX_COMMENT);
  }

  @Test
  void theExceptionNamesTheProblemInItsMessage() {
    assertThatThrownBy(() -> RatingRules.check(new RatingInput(null, null, null)))
        .isInstanceOf(InvalidRatingException.class)
        .hasMessageContaining("THUMB_REQUIRED");
  }
}
