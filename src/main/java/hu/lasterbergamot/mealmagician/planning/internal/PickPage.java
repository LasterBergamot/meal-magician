package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;

/**
 * What the pick sheet prints: the meals of the library to put on one day and slot type, and the
 * "Create and add" form.
 *
 * @param date the day the meal will be planned for
 * @param slotType the slot type it will be planned for
 * @param dayLabel the translated day, for example {@code Hétfő, nov. 2.}
 * @param query the search text as typed, stripped
 * @param meals at most {@link #LIMIT} library meals, recently planned first, then by name
 * @param more whether the library holds more matching meals than are listed
 * @param libraryEmpty whether the library has no meal at all (no search text and no match)
 * @param quick the state of the quick-add form
 */
public record PickPage(
    LocalDate date,
    SlotType slotType,
    String dayLabel,
    String query,
    List<MealView> meals,
    boolean more,
    boolean libraryEmpty,
    PickQuick quick) {

  /** How many meals the sheet lists at most; a search narrows the rest. */
  public static final int LIMIT = 20;

  /**
   * The day as the hidden {@code date} field carries it.
   *
   * @return for example {@code 2026-11-04}
   */
  public String dateValue() {
    return date.toString();
  }

  /**
   * The slot type as the hidden {@code slot} field carries it.
   *
   * @return {@code BREAKFAST}, {@code LUNCH}, {@code DINNER} or {@code SNACK}
   */
  public String slotName() {
    return slotType.name();
  }

  /**
   * The message key of the slot type's word.
   *
   * @return for example {@code slot.lunch}
   */
  public String labelKey() {
    return SlotGroup.labelKeyOf(slotType);
  }

  /**
   * The address of the week page that holds this day, for the "back" link of the no-JavaScript
   * page.
   *
   * @return for example {@code /plan?week=2026-W45}
   */
  public String weekHref() {
    return WeekPage.hrefOf(IsoWeek.containing(date));
  }
}
