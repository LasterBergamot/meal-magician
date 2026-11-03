package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.planning.SharedMeal;
import hu.lasterbergamot.mealmagician.planning.SharedOccasion;
import hu.lasterbergamot.mealmagician.planning.SharedWeek;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The view model of the public share page: a week's heading and seven days. It is built only from a
 * {@link SharedWeek}, so it can hold nothing but dates, meal types, meal names and occasion names
 * (PLAN.md D28). It carries no id, note, rating, member or household name, and not the link's
 * token.
 *
 * @param title the heading, for example {@code Week 45}
 * @param range the first and last day with the year
 * @param days Monday to Sunday, always seven
 * @param occasions the week's occasions in date order; empty when there are none
 */
public record SharedWeekPage(
    String title, String range, List<SharedDay> days, List<OccasionLine> occasions) {

  /**
   * One occasion of the week.
   *
   * @param name the occasion's name
   * @param dayLabel the weekday and date, for example {@code Monday, Nov 2}
   * @param typeLabel the translated kind (birthday, holiday, party, other)
   */
  public record OccasionLine(String name, String dayLabel, String typeLabel) {}

  /**
   * Builds the page for a week in the reader's language.
   *
   * @param week what the public reader returned
   * @param messages the translation helper; its locale decides the date formats and the words
   * @return the page; a week with nothing planned gives seven days with no slots
   */
  public static SharedWeekPage of(SharedWeek week, Messages messages) {
    Locale locale = messages.locale();
    LocalDate monday = week.monday();
    Map<LocalDate, List<SharedMeal>> byDate =
        week.meals().stream().collect(Collectors.groupingBy(SharedMeal::date));
    List<SharedDay> days =
        IntStream.range(0, 7)
            .mapToObj(monday::plusDays)
            .map(date -> day(date, byDate.getOrDefault(date, List.of()), messages, locale))
            .toList();
    List<OccasionLine> occasions =
        week.occasions().stream().map(o -> occasion(o, messages, locale)).toList();
    String number = String.valueOf(monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    return new SharedWeekPage(
        messages.t("share.public.week", number),
        messages.t(
            "share.public.range",
            ShareDates.mediumDate(monday, locale),
            ShareDates.mediumDate(monday.plusDays(6), locale)),
        days,
        occasions);
  }

  private static SharedDay day(
      LocalDate date, List<SharedMeal> meals, Messages messages, Locale locale) {
    List<SharedDay.Slot> slots = new ArrayList<>();
    // The enum order is the order of the day, and only types that hold a meal are listed (D30).
    for (SlotType type : SlotType.values()) {
      List<String> names =
          meals.stream().filter(m -> m.slotType() == type).map(SharedMeal::mealName).toList();
      if (!names.isEmpty()) {
        slots.add(
            new SharedDay.Slot(
                type.name(), messages.t("slot." + type.name().toLowerCase(Locale.ROOT)), names));
      }
    }
    return new SharedDay(
        date.toString(),
        ShareDates.dayName(date, locale),
        ShareDates.dateLabel(date, locale),
        List.copyOf(slots));
  }

  private static OccasionLine occasion(SharedOccasion occasion, Messages messages, Locale locale) {
    return new OccasionLine(
        occasion.name(),
        messages.t(
            "share.public.day",
            ShareDates.dayName(occasion.date(), locale),
            ShareDates.dateLabel(occasion.date(), locale)),
        messages.t("meal.occasion." + occasion.type().name().toLowerCase(Locale.ROOT)));
  }
}
