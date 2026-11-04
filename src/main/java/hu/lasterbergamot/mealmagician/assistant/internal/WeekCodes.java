package hu.lasterbergamot.mealmagician.assistant.internal;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes the week codes of the plan page ({@code 2026-W45}). The planner has the same
 * logic in its own internal package, which the assistant must not import, so this is a small copy
 * that is deliberately lenient: anything that is not a real week means "the current week", exactly
 * like the plan page does.
 */
final class WeekCodes {

  private static final Pattern CODE = Pattern.compile("^(\\d{4})-W(\\d{2})$");

  private WeekCodes() {}

  /**
   * The Monday of the week a request names.
   *
   * @param code text such as {@code 2026-W45}, or {@code null}
   * @param clock the application clock, for the fallback
   * @return the Monday of that week; the current week's Monday for {@code null}, a malformed code
   *     or a week the year does not have
   */
  static LocalDate mondayOf(String code, Clock clock) {
    LocalDate current =
        LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    if (code == null) {
      return current;
    }
    Matcher match = CODE.matcher(code);
    if (!match.matches()) {
      return current;
    }
    int year = Integer.parseInt(match.group(1));
    int week = Integer.parseInt(match.group(2));
    // 4 January is always in week 1 of its week-based year, so it is a safe anchor.
    LocalDate january4 = LocalDate.of(year, 1, 4);
    long lastWeek = IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(january4).getMaximum();
    if (week < 1 || week > lastWeek) {
      return current;
    }
    return january4
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * The code of the week that contains a day.
   *
   * @param day any date
   * @return text such as {@code 2026-W45}, which {@link #mondayOf} reads back
   */
  static String codeOf(LocalDate day) {
    return "%04d-W%02d"
        .formatted(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
  }
}
