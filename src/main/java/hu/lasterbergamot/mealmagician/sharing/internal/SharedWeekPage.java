package hu.lasterbergamot.mealmagician.sharing.internal;

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
 * The view model of the public share page and of the members' print page: a week's heading and
 * seven days. {@link #of} builds the public one from a {@link SharedWeek}, so it can hold nothing
 * but dates, meal types, meal names and occasion names (PLAN.md D28). It carries no id, note,
 * rating, member or household name, and not the link's token. {@link #forPrint} builds the same
 * shape for signed-in members and adds the slot notes (PLAN.md D29); that page is never reachable
 * without a sign-in.
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
   * One planned meal as the print page knows it: the public fields plus the slot's note.
   *
   * @param date the day
   * @param slotType the meal type
   * @param mealName the meal's name
   * @param note the slot note, or {@code null} for none
   */
  public record PrintMeal(LocalDate date, SlotType slotType, String mealName, String note) {}

  /**
   * Builds the public page for a week in the reader's language. It never shows notes.
   *
   * @param week what the public reader returned
   * @param messages the translation helper; its locale decides the date formats and the words
   * @return the page; a week with nothing planned gives seven days with no slots
   */
  public static SharedWeekPage of(SharedWeek week, Messages messages) {
    List<PrintMeal> meals =
        week.meals().stream()
            .map(m -> new PrintMeal(m.date(), m.slotType(), m.mealName(), null))
            .toList();
    return build(week.monday(), meals, week.occasions(), messages, false);
  }

  /**
   * Builds the print page of a week for signed-in members. It is the public page's shape with the
   * slot notes added, so the two cannot drift apart.
   *
   * @param monday the Monday of the week
   * @param meals the planned meals ordered by date, meal type and position
   * @param occasions the week's occasions in date order
   * @param messages the translation helper
   * @return the page, with a note beside each meal that has one
   */
  public static SharedWeekPage forPrint(
      LocalDate monday, List<PrintMeal> meals, List<SharedOccasion> occasions, Messages messages) {
    return build(monday, meals, occasions, messages, true);
  }

  private static SharedWeekPage build(
      LocalDate monday,
      List<PrintMeal> meals,
      List<SharedOccasion> shared,
      Messages messages,
      boolean withNotes) {
    Locale locale = messages.locale();
    Map<LocalDate, List<PrintMeal>> byDate =
        meals.stream().collect(Collectors.groupingBy(PrintMeal::date));
    List<SharedDay> days =
        IntStream.range(0, 7)
            .mapToObj(monday::plusDays)
            .map(date ->
                day(date, byDate.getOrDefault(date, List.of()), messages, locale, withNotes))
            .toList();
    List<OccasionLine> occasions = shared.stream().map(o -> occasion(o, messages, locale)).toList();
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
      LocalDate date, List<PrintMeal> meals, Messages messages, Locale locale, boolean withNotes) {
    List<SharedDay.Slot> slots = new ArrayList<>();
    // The enum order is the order of the day, and only types that hold a meal are listed (D30).
    for (SlotType type : SlotType.values()) {
      List<PrintMeal> ofType = meals.stream().filter(m -> m.slotType() == type).toList();
      if (!ofType.isEmpty()) {
        List<String> notes =
            withNotes
                ? ofType.stream().map(m -> m.note() == null ? "" : m.note()).toList()
                : List.of();
        slots.add(
            new SharedDay.Slot(
                type.name(),
                messages.t("slot." + type.name().toLowerCase(Locale.ROOT)),
                ofType.stream().map(PrintMeal::mealName).toList(),
                notes));
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
