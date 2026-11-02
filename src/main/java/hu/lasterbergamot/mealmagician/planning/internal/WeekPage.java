package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.util.Arrays;
import java.util.List;

/**
 * Everything the week page prints: the week, its heading texts and its seven days. The phone's day
 * list reads {@link #days()}; the desktop table reads {@link #rows()}.
 *
 * @param week the week shown
 * @param title the translated heading, for example {@code 45. hét}
 * @param range the translated first and last date, for example {@code 2026. nov. 2. – 2026. nov.
 *     8.}
 * @param current whether today is in this week; if not the page offers a way back
 * @param days the seven days, Monday first
 * @param conflict whether the page opens with the "someone else changed the plan" banner, which a
 *     stale edit without htmx gets
 * @param occasions the week's occasions, shown as links above the days
 * @param reuseError the message key of a refused copy that a request without htmx shows as a
 *     banner, or {@code null}
 */
public record WeekPage(
    IsoWeek week,
    String title,
    String range,
    boolean current,
    List<DayPlan> days,
    boolean conflict,
    List<OccasionChip> occasions,
    String reuseError) {

  /**
   * The same page with the conflict banner switched on.
   *
   * @return a copy whose {@link #conflict()} is {@code true}
   */
  public WeekPage withConflict() {
    return new WeekPage(week, title, range, current, days, true, occasions, reuseError);
  }

  /**
   * The same page with the "nothing to copy" or "same target" banner switched on.
   *
   * @param key the message key of the refusal
   * @return a copy whose {@link #reuseError()} is {@code key}
   */
  public WeekPage withReuseError(String key) {
    return new WeekPage(week, title, range, current, days, conflict, occasions, key);
  }

  /**
   * The address of this week's reuse sheet, which also works as a page without JavaScript.
   *
   * @return for example {@code /plan/reuse/week?week=2026-W45}
   */
  public String reuseHref() {
    return "/plan/reuse/week?week=" + week.code();
  }

  /**
   * The address of a week's page.
   *
   * @param week the week
   * @return for example {@code /plan?week=2026-W45}
   */
  public static String hrefOf(IsoWeek week) {
    return "/plan?week=" + week.code();
  }

  /**
   * The address of this page's week.
   *
   * @return the week's own address
   */
  public String href() {
    return hrefOf(week);
  }

  /**
   * The address of the week before.
   *
   * @return the previous week's address
   */
  public String previousHref() {
    return hrefOf(week.previous());
  }

  /**
   * The address of the week after.
   *
   * @return the next week's address
   */
  public String nextHref() {
    return hrefOf(week.next());
  }

  /**
   * The desktop table's rows: one per slot type, each with the seven days' groups.
   *
   * @return four rows in {@link SlotType} order
   */
  public List<SlotRow> rows() {
    return Arrays.stream(SlotType.values())
        .map(type -> new SlotRow(type, days.stream().map(day -> day.group(type)).toList()))
        .toList();
  }
}
