package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.List;

/**
 * The first step of "Suggest my week": the request form (plan-ui-redesign.md Appendix D.3, step 1).
 * It is also what an error brings back, with the member's text and chips kept.
 *
 * @param weekCode the week as {@code YYYY-Www}, posted back with the form
 * @param range the week's dates, already formatted for the member's language
 * @param request what the member typed so far (never the chips' phrases)
 * @param hints the quick-request chips that are ticked, as their keys
 * @param requestError the message key of the field's own error, or {@code null}
 * @param notice an alert to show above the form, or {@code null}
 */
public record WeekSheetPage(
    String weekCode,
    String range,
    String request,
    List<String> hints,
    String requestError,
    ErrorNotice notice) {

  /**
   * The form with nothing typed yet.
   *
   * @param weekCode the week as {@code YYYY-Www}
   * @param range the week's dates, formatted
   * @return an empty form
   */
  public static WeekSheetPage blank(String weekCode, String range) {
    return new WeekSheetPage(weekCode, range, "", List.of(), null, null);
  }
}
