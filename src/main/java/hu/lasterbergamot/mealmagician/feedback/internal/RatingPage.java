package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.RatingProblem;
import hu.lasterbergamot.mealmagician.feedback.RatingView;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * What the rating form prints: the slot it is about, the member's own rating (or what they just
 * typed), the other members' ratings and the outcome of the last post.
 *
 * @param slotId the slot being rated
 * @param date the slot's day
 * @param mealName the planned meal's name
 * @param dayLabel the translated day, for example {@code Hétfő, nov. 2.}
 * @param slotName the slot type's enum name, for the slot chip
 * @param slotKey the message key of the slot type's word, for example {@code slot.lunch}
 * @param form the form's values: the saved rating, or the typed one after a rejection
 * @param rated whether the member has a saved rating, which decides whether Clear is offered
 * @param others the other current members' ratings, by display name; never an e-mail address
 * @param errorKey the message key of the rejection, or {@code null}
 * @param saved whether this answer follows a successful save, which shows the thanks line
 */
public record RatingPage(
    long slotId,
    LocalDate date,
    String mealName,
    String dayLabel,
    String slotName,
    String slotKey,
    RatingForm form,
    boolean rated,
    List<RatingView> others,
    String errorKey,
    boolean saved) {

  /**
   * The message key that explains a rejected rating.
   *
   * @param problem the rule the service reported
   * @return a key in the {@code rating.error.*} block
   */
  public static String errorKeyOf(RatingProblem problem) {
    return switch (problem) {
      case THUMB_REQUIRED -> "rating.error.thumb";
      case STARS_OUT_OF_RANGE -> "rating.error.stars";
      case COMMENT_TOO_LONG -> "rating.error.comment";
      case NOT_YET -> "rating.error.notyet";
    };
  }

  /**
   * The address the form posts to.
   *
   * @return for example {@code /feedback/slots/7}
   */
  public String action() {
    return "/feedback/slots/" + slotId;
  }

  /**
   * The address the Clear button posts to.
   *
   * @return for example {@code /feedback/slots/7/clear}
   */
  public String clearAction() {
    return action() + "/clear";
  }

  /**
   * The week page that holds the slot, for the back link of the no-JavaScript page. The code is
   * built here because {@code planning}'s own helper is internal to that module.
   *
   * @return for example {@code /plan?week=2026-W45}
   */
  public String weekHref() {
    return "/plan?week=%d-W%02d"
        .formatted(
            date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
  }

  /**
   * Tells whether any other member has rated this slot.
   *
   * @return {@code true} when {@link #others} is not empty
   */
  public boolean hasOthers() {
    return !others.isEmpty();
  }
}
