package hu.lasterbergamot.mealmagician.assistant.internal;

/**
 * An error the assistant sheet shows as an alert (plan-ui-redesign.md Appendix D.3, step 5). The
 * member's text is kept in the form beside it, so the person can try again.
 *
 * @param kind the {@code ui/_alert} kind: {@code warning} for the spend cap, {@code danger} for an
 *     unavailable model or a draft that cannot be accepted
 * @param messageKey the message bundle key of the sentence
 * @param retry whether the alert carries a "Try again" button that sends the same form again
 */
public record ErrorNotice(String kind, String messageKey, boolean retry) {

  /**
   * The household's monthly allowance is used up; trying again would not help.
   *
   * @return the notice
   */
  public static ErrorNotice cap() {
    return new ErrorNotice("warning", "assistant.error.cap", false);
  }

  /**
   * The model failed, timed out or answered with something unusable; trying again may help.
   *
   * @return the notice
   */
  public static ErrorNotice unavailable() {
    return new ErrorNotice("danger", "assistant.error.unavailable", true);
  }

  /**
   * The draft cannot be written into the plan right now.
   *
   * @return the notice
   */
  public static ErrorNotice acceptRefused() {
    return new ErrorNotice("danger", "assistant.error.accept", false);
  }
}
