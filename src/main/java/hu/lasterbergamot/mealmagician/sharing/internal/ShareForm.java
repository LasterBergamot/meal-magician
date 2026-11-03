package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.sharing.ShareExpiry;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the share routes read from the request: which ISO week, and for a new link how long it
 * should work. The print route uses {@link #mondayOf} for the week alone.
 *
 * <p>The week code is the same {@code 2026-W45} the plan page uses. The planning module keeps its
 * own week parser in an {@code internal} package that this module may not import, so the small
 * parser is repeated here. Anything that is not a real week falls back to the current week, exactly
 * like the plan page, instead of failing: a stale bookmark should still open something useful.
 *
 * @param monday the Monday of the week
 * @param expiry the chosen lifetime; the default of 7 days for a missing or unknown value
 */
record ShareForm(LocalDate monday, ShareExpiry expiry) {

  /** A strict code: four digits, {@code -W}, two digits. Nothing else reaches the date maths. */
  private static final Pattern CODE = Pattern.compile("^(\\d{4})-W(\\d{2})$");

  /**
   * Reads the request values.
   *
   * @param week the {@code week} parameter, may be {@code null} or malformed
   * @param expiry the {@code expiry} parameter ({@code 1d}, {@code 7d}, {@code 30d}), may be {@code
   *     null}
   * @param clock the application clock, for the fallback to the current week
   * @return the parsed form; never {@code null} and never throws
   */
  static ShareForm of(String week, String expiry, Clock clock) {
    return new ShareForm(mondayOf(week, clock), ShareExpiry.fromCode(expiry));
  }

  /**
   * Reads only the week.
   *
   * @param week the {@code week} parameter, may be {@code null} or malformed
   * @param clock the application clock, for the fallback to the current week
   * @return the Monday of that week, or of the current week when the code is not a real week
   */
  static LocalDate mondayOf(String week, Clock clock) {
    LocalDate current =
        LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    if (week == null) {
      return current;
    }
    Matcher match = CODE.matcher(week);
    if (!match.matches()) {
      return current;
    }
    int year = Integer.parseInt(match.group(1));
    int number = Integer.parseInt(match.group(2));
    // 4 January is always in week 1 of its week-based year, so it is a safe anchor.
    LocalDate january4 = LocalDate.of(year, 1, 4);
    long last = IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(january4).getMaximum();
    if (number < 1 || number > last) {
      return current;
    }
    return january4
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, number)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * The week code of a Monday, to put back into links and hidden fields.
   *
   * @param monday the first day of the week
   * @return text such as {@code 2026-W45}, which {@link #mondayOf} reads back
   */
  static String codeOf(LocalDate monday) {
    return "%04d-W%02d"
        .formatted(
            monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
  }

  /**
   * The number of a Monday's week in its week-based year.
   *
   * @param monday the first day of the week
   * @return 1 to 53
   */
  static int numberOf(LocalDate monday) {
    return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
  }
}
