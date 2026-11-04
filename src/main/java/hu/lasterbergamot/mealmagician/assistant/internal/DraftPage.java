package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.List;

/**
 * A draft as the sheet shows it (plan-ui-redesign.md Appendix D.3, step 3): grouped by day, library
 * meals as rows and ideas as dashed cards, with the refine form and the accept footer. Everything
 * the model wrote ({@code summary}, names, reasons, ingredients) is data: the templates escape it.
 *
 * @param id the draft's id
 * @param weekCode the week as {@code YYYY-Www}
 * @param range the week's dates, already formatted for the member's language
 * @param summary the model's one-line summary, or {@code null}
 * @param libraryCount how many items are meals from the library
 * @param ideaCount how many items are ideas that are not in the library yet
 * @param days the days that have items, in date order
 * @param refineText what the member typed in the follow-up box (kept after an error)
 * @param refineHints the ticked quick-request chips of the follow-up form, as their keys
 * @param refineError the message key of the follow-up field's own error, or {@code null}
 * @param notice an alert to show above the follow-up form, or {@code null}
 */
public record DraftPage(
    long id,
    String weekCode,
    String range,
    String summary,
    int libraryCount,
    int ideaCount,
    List<Day> days,
    String refineText,
    List<String> refineHints,
    String refineError,
    ErrorNotice notice) {

  /**
   * One day of the draft.
   *
   * @param domId an element id for the day's heading
   * @param label the day, formatted for the member's language
   * @param items the day's items in slot order
   */
  public record Day(String domId, String label, List<Item> items) {}

  /**
   * One proposed meal.
   *
   * @param id the item's id, for "remove" and "add to meals"
   * @param idea {@code true} for an idea that is not in the library yet
   * @param slot the slot type's name for {@code ui/_slot_chip}, for example {@code DINNER}
   * @param slotLabelKey the message key of the slot type's word
   * @param name the meal's or the idea's name
   * @param reason the model's reason, or {@code null}
   * @param ingredients the idea's ingredients as one line, or {@code null}
   * @param justAdded {@code true} for an idea the member has just added to the library, which is
   *     now a normal row with a note; focus lands on it
   */
  public record Item(
      long id,
      boolean idea,
      String slot,
      String slotLabelKey,
      String name,
      String reason,
      String ingredients,
      boolean justAdded) {}

  /**
   * Whether the draft has anything the plan could take.
   *
   * @return {@code true} if at least one item is a library meal
   */
  public boolean acceptable() {
    return libraryCount > 0;
  }

  /**
   * Whether the draft has no item left at all.
   *
   * @return {@code true} if the member removed everything
   */
  public boolean empty() {
    return days.isEmpty();
  }

  /**
   * Whether the member has just added an idea to the library, in which case that row, and not the
   * draft's heading, takes focus after the swap.
   *
   * @return {@code true} if one item is flagged {@code justAdded}
   */
  public boolean anyJustAdded() {
    return days.stream().flatMap(day -> day.items().stream()).anyMatch(Item::justAdded);
  }
}
