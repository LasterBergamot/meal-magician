package hu.lasterbergamot.mealmagician.planning.internal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Formats the dates of the week page in the reader's language. The formatting is done here, in the
 * page model, and never in a template, so a template only prints text it was given.
 */
final class PlanDates {

  private PlanDates() {}

  /**
   * The full weekday name with a capital first letter (Hungarian writes it in lower case).
   *
   * @param date the day
   * @param locale the reader's language
   * @return for example {@code Hétfő} or {@code Monday}
   */
  static String dayName(LocalDate date, Locale locale) {
    String name = date.format(DateTimeFormatter.ofPattern("EEEE", locale));
    return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
  }

  /**
   * The abbreviated weekday name.
   *
   * @param date the day
   * @param locale the reader's language
   * @return for example {@code H} or {@code Mon}, as the locale abbreviates it
   */
  static String shortName(LocalDate date, Locale locale) {
    return date.format(DateTimeFormatter.ofPattern("EEE", locale));
  }

  /**
   * Month and day without the year, in the locale's own order.
   *
   * @param date the day
   * @param locale the reader's language
   * @return for example {@code nov. 2.} or {@code Nov 2}
   */
  static String dateLabel(LocalDate date, Locale locale) {
    return date.format(DateTimeFormatter.ofLocalizedPattern("MMMd").withLocale(locale));
  }

  /**
   * A medium date with the year.
   *
   * @param date the day
   * @param locale the reader's language
   * @return for example {@code 2026. nov. 2.} or {@code Nov 2, 2026}
   */
  static String mediumDate(LocalDate date, Locale locale) {
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
  }

  /**
   * The long date with the weekday, for a screen reader's label.
   *
   * @param date the day
   * @param locale the reader's language
   * @return for example {@code Monday, November 2, 2026}
   */
  static String fullLabel(LocalDate date, Locale locale) {
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale));
  }
}
