package hu.lasterbergamot.mealmagician.sharing.internal;

import java.util.List;

/**
 * One day of the public share page, ready to print.
 *
 * @param isoDate the date as {@code YYYY-MM-DD}, for the {@code datetime} attribute of a {@code
 *     <time>} element
 * @param dayName the weekday, for example {@code Monday}
 * @param dateLabel the month and day, for example {@code Nov 2}
 * @param slots only the meal types that hold a meal, in the order of the day; empty for a day with
 *     nothing planned (the page then shows a dash, D30)
 */
public record SharedDay(String isoDate, String dayName, String dateLabel, List<Slot> slots) {

  /**
   * The meals of one meal type on one day.
   *
   * @param type the type name ({@code BREAKFAST}, {@code LUNCH}, {@code DINNER} or {@code SNACK}),
   *     which picks the chip colour and icon
   * @param label the translated word for the type
   * @param meals the meal names in plan order; never empty
   */
  public record Slot(String type, String label, List<String> meals) {}
}
