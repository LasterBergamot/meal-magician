package hu.lasterbergamot.mealmagician.feedback.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.feedback.RatingInput;
import hu.lasterbergamot.mealmagician.feedback.RatingProblem;
import hu.lasterbergamot.mealmagician.feedback.RatingView;
import hu.lasterbergamot.mealmagician.feedback.Thumb;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RatingModelsTest {

  private static RatingView view(Integer stars, String comment) {
    return new RatingView(
        1,
        2,
        3,
        LocalDate.of(2026, 11, 2),
        SlotType.LUNCH,
        4,
        "Anna",
        Thumb.DOWN,
        stars,
        comment,
        true,
        Instant.EPOCH);
  }

  private static RatingPage page(List<RatingView> others, LocalDate date) {
    return new RatingPage(
        7,
        date,
        "Leves",
        "Hétfő",
        "LUNCH",
        "slot.lunch",
        RatingForm.empty(),
        false,
        others,
        null,
        false);
  }

  @Test
  void anEmptyFormChoosesNothing() {
    RatingForm form = RatingForm.empty();

    assertThat(form.up()).isFalse();
    assertThat(form.down()).isFalse();
    assertThat(form.noStars()).isTrue();
    assertThat(form.hasStars(3)).isFalse();
    assertThat(form.toInput()).isEqualTo(new RatingInput(null, null, ""));
  }

  @Test
  void aSavedRatingFillsTheFormWithItsThumbStarsAndComment() {
    RatingForm form = RatingForm.of(view(4, "Finom"));

    assertThat(form).isEqualTo(new RatingForm("DOWN", "4", "Finom"));
    assertThat(form.down()).isTrue();
    assertThat(form.up()).isFalse();
    assertThat(form.hasStars(4)).isTrue();
    assertThat(form.noStars()).isFalse();
  }

  @Test
  void aSavedRatingWithoutStarsOrCommentFillsEmptyStrings() {
    assertThat(RatingForm.of(view(null, null))).isEqualTo(new RatingForm("DOWN", "", ""));
  }

  @Test
  void missingParametersBecomeEmptyStringsAndPostedOnesAreKept() {
    assertThat(RatingForm.parse(null, null, null)).isEqualTo(RatingForm.empty());
    assertThat(RatingForm.parse("UP", " 5 ", "ok")).isEqualTo(new RatingForm("UP", " 5 ", "ok"));
  }

  @Test
  void theInputParsesLeniently() {
    assertThat(RatingForm.parse("UP", "5", "x").toInput())
        .isEqualTo(new RatingInput(Thumb.UP, 5, "x"));
    assertThat(RatingForm.parse("DOWN", " 2 ", "").toInput().stars()).isEqualTo(2);
    assertThat(RatingForm.parse("up", "", "").toInput().thumb()).isNull();
    assertThat(RatingForm.parse("UP", "abc", "").toInput().stars())
        .isEqualTo(RatingForm.UNPARSEABLE_STARS);
    assertThat(RatingForm.parse("UP", "  ", "").toInput().stars()).isNull();
  }

  @Test
  void everyProblemHasItsOwnMessageKey() {
    assertThat(RatingPage.errorKeyOf(RatingProblem.THUMB_REQUIRED)).isEqualTo("rating.error.thumb");
    assertThat(RatingPage.errorKeyOf(RatingProblem.STARS_OUT_OF_RANGE))
        .isEqualTo("rating.error.stars");
    assertThat(RatingPage.errorKeyOf(RatingProblem.COMMENT_TOO_LONG))
        .isEqualTo("rating.error.comment");
    assertThat(RatingPage.errorKeyOf(RatingProblem.NOT_YET)).isEqualTo("rating.error.notyet");
  }

  @Test
  void aRatingPageBuildsItsAddressesAndKnowsWhetherThereAreOthers() {
    RatingPage empty = page(List.of(), LocalDate.of(2026, 11, 2));
    RatingPage withOthers = page(List.of(view(null, null)), LocalDate.of(2026, 11, 2));

    assertThat(empty.action()).isEqualTo("/feedback/slots/7");
    assertThat(empty.clearAction()).isEqualTo("/feedback/slots/7/clear");
    assertThat(empty.weekHref()).isEqualTo("/plan?week=2026-W45");
    assertThat(empty.hasOthers()).isFalse();
    assertThat(withOthers.hasOthers()).isTrue();
  }

  @Test
  void theWeekCodeUsesTheIsoWeekYearAcrossTheNewYear() {
    assertThat(page(List.of(), LocalDate.of(2021, 1, 3)).weekHref())
        .isEqualTo("/plan?week=2020-W53");
    assertThat(page(List.of(), LocalDate.of(2026, 1, 5)).weekHref())
        .isEqualTo("/plan?week=2026-W02");
  }

  @Test
  void aPromptPageLinksToTheSlotSheet() {
    PromptPage prompt = new PromptPage(9, "Leves", "Hétfő", "LUNCH", "slot.lunch");

    assertThat(prompt.slotHref()).isEqualTo("/plan/slots/9");
  }
}
