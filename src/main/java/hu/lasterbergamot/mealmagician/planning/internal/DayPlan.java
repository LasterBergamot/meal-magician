package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;

/**
 * One day of the week page with its texts already formatted for the reader's language.
 *
 * @param date the day
 * @param name the full weekday name, for example {@code Hétfő}
 * @param dateLabel month and day, for example {@code nov. 2.}
 * @param shortName the abbreviated weekday name
 * @param fullLabel the long date, for the week strip's accessible name
 * @param today whether this is the current day, which gets the gold treatment
 * @param groups the day's four slot groups, always in {@link SlotType} order (the phone shows only
 *     {@link #shownGroups()} up front and keeps {@link #hiddenGroups()} behind a disclosure)
 */
public record DayPlan(
    LocalDate date,
    String name,
    String dateLabel,
    String shortName,
    String fullLabel,
    boolean today,
    List<SlotGroup> groups) {

  /**
   * The element id of a day's section, which the week strip links to.
   *
   * @param date the day
   * @return for example {@code day-2026-11-02}
   */
  public static String idOf(LocalDate date) {
    return "day-" + date;
  }

  /**
   * This day's element id.
   *
   * @return the same as {@link #idOf} for {@link #date()}
   */
  public String id() {
    return idOf(date);
  }

  /**
   * The address of this day's reuse sheet, which also works as a page without JavaScript.
   *
   * @return for example {@code /plan/reuse/day?date=2026-11-02}
   */
  public String reuseHref() {
    return "/plan/reuse/day?date=" + date;
  }

  /**
   * The day of the month, for the big number in the strip and the table header.
   *
   * @return 1 to 31
   */
  public int dayOfMonth() {
    return date.getDayOfMonth();
  }

  /**
   * One of the day's groups.
   *
   * @param type the slot type
   * @return the group of that type (there is always one, empty or not)
   */
  public SlotGroup group(SlotType type) {
    return groups.get(type.ordinal());
  }

  /**
   * The groups the phone's day card draws up front: Lunch (the household's main meal, D30) and any
   * other slot that already holds a meal, so a planned breakfast never hides.
   *
   * @return the shown groups in {@link SlotType} order; never empty, because Lunch is always in it
   */
  public List<SlotGroup> shownGroups() {
    return groups.stream().filter(DayPlan::isShown).toList();
  }

  /**
   * The empty optional groups, which the phone's day card keeps behind the "Other meals"
   * disclosure. Together with {@link #shownGroups()} they are all of {@link #groups()}.
   *
   * @return the groups that are not Lunch and hold no meal, in {@link SlotType} order; possibly
   *     empty (then the card draws no disclosure)
   */
  public List<SlotGroup> hiddenGroups() {
    return groups.stream().filter(group -> !isShown(group)).toList();
  }

  private static boolean isShown(SlotGroup group) {
    return group.type() == SlotType.LUNCH || !group.empty();
  }
}
