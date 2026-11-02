package hu.lasterbergamot.mealmagician.planning.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * One ISO 8601 week: it starts on a Monday and ends on the Sunday after. The week page is addressed
 * by its code, for example {@code 2026-W45}, which is what the {@code week} request parameter
 * carries.
 *
 * <p>An ISO week belongs to the year that holds its Thursday, so {@code 2027-01-01} (a Friday) is
 * in week {@code 2026-W53} and {@code 2025-12-31} is in {@code 2026-W01}. That is why the code uses
 * the <em>week-based</em> year and not the calendar year of the Monday.
 *
 * @param monday the first day of the week; the constructor refuses any other weekday
 */
public record IsoWeek(LocalDate monday) {

  /** A strict code: four digits, {@code -W}, two digits. Nothing else reaches the date maths. */
  private static final Pattern CODE = Pattern.compile("^(\\d{4})-W(\\d{2})$");

  /**
   * Checks that the date is a Monday.
   *
   * @throws IllegalArgumentException if {@code monday} is not a Monday
   */
  public IsoWeek {
    if (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new IllegalArgumentException("Not a Monday: " + monday);
    }
  }

  /**
   * The week a day belongs to.
   *
   * @param day any date
   * @return the week that contains it
   */
  public static IsoWeek containing(LocalDate day) {
    return new IsoWeek(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
  }

  /**
   * Reads a week code from a request. Anything that is not a real week gives an empty result, so
   * the caller can fall back to the current week instead of failing.
   *
   * @param code text such as {@code 2026-W45}, or {@code null}
   * @return the week, or empty for {@code null}, a malformed code, week 0 or a week the year does
   *     not have (for example {@code 2021-W53}; 2021 has only 52)
   */
  public static Optional<IsoWeek> parse(String code) {
    if (code == null) {
      return Optional.empty();
    }
    Matcher match = CODE.matcher(code);
    if (!match.matches()) {
      return Optional.empty();
    }
    int year = Integer.parseInt(match.group(1));
    int week = Integer.parseInt(match.group(2));
    // 4 January is always in week 1 of its week-based year, so it is a safe anchor.
    LocalDate january4 = LocalDate.of(year, 1, 4);
    long lastWeek = IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(january4).getMaximum();
    if (week < 1 || week > lastWeek) {
      return Optional.empty();
    }
    LocalDate inWeek = january4.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week);
    return Optional.of(containing(inWeek));
  }

  /**
   * The week's code.
   *
   * @return text such as {@code 2026-W45}; {@link #parse} reads it back
   */
  public String code() {
    return "%04d-W%02d"
        .formatted(
            monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
  }

  /**
   * The week's number in its week-based year.
   *
   * @return 1 to 53
   */
  public int number() {
    return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
  }

  /**
   * The last day of the week.
   *
   * @return the Sunday
   */
  public LocalDate sunday() {
    return monday.plusDays(6);
  }

  /**
   * The week before.
   *
   * @return the week that starts seven days earlier
   */
  public IsoWeek previous() {
    return new IsoWeek(monday.minusWeeks(1));
  }

  /**
   * The week after.
   *
   * @return the week that starts seven days later
   */
  public IsoWeek next() {
    return new IsoWeek(monday.plusWeeks(1));
  }

  /**
   * The seven dates of the week.
   *
   * @return Monday first, Sunday last
   */
  public List<LocalDate> days() {
    return IntStream.range(0, 7).mapToObj(monday::plusDays).toList();
  }

  /**
   * Tells whether a date falls in this week.
   *
   * @param day any date
   * @return {@code true} from Monday to Sunday, both included
   */
  public boolean contains(LocalDate day) {
    return !day.isBefore(monday) && !day.isAfter(sunday());
  }
}
