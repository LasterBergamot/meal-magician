package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.List;

/**
 * The wizard's region of the meal-pick sheet (plan-ui-redesign.md Appendix D.4): the "ask" row, or
 * up to three suggestions, or an error. The same region swaps itself.
 *
 * @param date the slot's day as {@code YYYY-MM-DD}, posted back by the ask row
 * @param slot the slot type's name, for example {@code DINNER}
 * @param answered {@code true} once the wizard has answered (with {@code rows}, possibly none)
 * @param rows the suggested library meals, best first
 * @param notice an alert to show above the ask row, or {@code null}
 */
public record SlotSuggestionPage(
    String date, String slot, boolean answered, List<Row> rows, ErrorNotice notice) {

  /**
   * One suggested meal.
   *
   * @param mealId the library meal, which the existing "add to slot" form plans
   * @param name the meal's name
   * @param reason the model's reason, or {@code null}; escaped by the template
   */
  public record Row(long mealId, String name, String reason) {}

  /**
   * The row that asks the wizard, before anything was asked.
   *
   * @param date the slot's day as {@code YYYY-MM-DD}
   * @param slot the slot type's name
   * @return the ask row alone
   */
  public static SlotSuggestionPage ask(String date, String slot) {
    return new SlotSuggestionPage(date, slot, false, List.of(), null);
  }

  /**
   * The wizard's answer.
   *
   * @param date the slot's day as {@code YYYY-MM-DD}
   * @param slot the slot type's name
   * @param rows the suggestions, possibly none
   * @return the answer
   */
  public static SlotSuggestionPage answer(String date, String slot, List<Row> rows) {
    return new SlotSuggestionPage(date, slot, true, rows, null);
  }

  /**
   * The ask row with an error above it.
   *
   * @param date the slot's day as {@code YYYY-MM-DD}
   * @param slot the slot type's name
   * @param notice what went wrong
   * @return the ask row and the alert
   */
  public static SlotSuggestionPage failed(String date, String slot, ErrorNotice notice) {
    return new SlotSuggestionPage(date, slot, false, List.of(), notice);
  }
}
