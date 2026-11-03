package hu.lasterbergamot.mealmagician.sharing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.SharedMeal;
import hu.lasterbergamot.mealmagician.planning.SharedOccasion;
import hu.lasterbergamot.mealmagician.planning.SharedWeek;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.i18n.I18nConfig;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

/** The model of the public share page in both languages: dates, slots, empty days, occasions. */
class SharedWeekPageTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private final I18nConfig config = new I18nConfig();
  private final Messages messages = config.messages(config.messageSource());

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void anEmptyWeekHasSevenDaysWithNoSlotsAndNoOccasions() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    SharedWeekPage page = SharedWeekPage.of(new SharedWeek(MONDAY, List.of(), List.of()), messages);

    assertThat(page.title()).isEqualTo("Week 45");
    assertThat(page.days()).hasSize(7);
    assertThat(page.days()).allSatisfy(day -> assertThat(day.slots()).isEmpty());
    assertThat(page.occasions()).isEmpty();
  }

  @Test
  void daysRunFromMondayToSundayWithNamesAndDatesInEnglish() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    SharedWeekPage page = SharedWeekPage.of(new SharedWeek(MONDAY, List.of(), List.of()), messages);

    assertThat(page.days())
        .extracting(SharedDay::dayName)
        .containsExactly(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
    assertThat(page.days().get(0).isoDate()).isEqualTo("2026-11-02");
    assertThat(page.days().get(0).dateLabel()).isEqualTo("Nov 2");
    assertThat(page.days().get(6).isoDate()).isEqualTo("2026-11-08");
    assertThat(page.range()).isEqualTo("Nov 2, 2026 – Nov 8, 2026");
  }

  @Test
  void daysAndHeadingAreInHungarian() {
    LocaleContextHolder.setLocale(Locale.forLanguageTag("hu"));

    SharedWeekPage page = SharedWeekPage.of(new SharedWeek(MONDAY, List.of(), List.of()), messages);

    assertThat(page.title()).isEqualTo("45. hét");
    assertThat(page.days().get(0).dayName()).isEqualTo("Hétfő");
    assertThat(page.days().get(6).dayName()).isEqualTo("Vasárnap");
    assertThat(page.range()).isEqualTo("2026. nov. 2. – 2026. nov. 8.");
  }

  @Test
  void mealsAreGroupedBySlotTypeInTheOrderOfTheDayAndEmptyTypesAreLeftOut() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    SharedWeek week =
        new SharedWeek(
            MONDAY,
            List.of(
                new SharedMeal(MONDAY, SlotType.DINNER, 0, "Soup"),
                new SharedMeal(MONDAY, SlotType.BREAKFAST, 0, "Eggs"),
                new SharedMeal(MONDAY, SlotType.BREAKFAST, 1, "Toast"),
                new SharedMeal(MONDAY.plusDays(1), SlotType.LUNCH, 0, "Stew")),
            List.of());

    SharedWeekPage page = SharedWeekPage.of(week, messages);

    SharedDay monday = page.days().get(0);
    assertThat(monday.slots())
        .extracting(SharedDay.Slot::type, SharedDay.Slot::label, SharedDay.Slot::meals)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("BREAKFAST", "Breakfast", List.of("Eggs", "Toast")),
            org.assertj.core.groups.Tuple.tuple("DINNER", "Dinner", List.of("Soup")));
    assertThat(page.days().get(1).slots())
        .extracting(SharedDay.Slot::type)
        .containsExactly("LUNCH");
    assertThat(page.days().get(2).slots()).isEmpty();
  }

  @Test
  void occasionsCarryTheirNameDayAndTranslatedKind() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    SharedWeek week =
        new SharedWeek(
            MONDAY,
            List.of(),
            List.of(new SharedOccasion(MONDAY.plusDays(4), "Anna's party", OccasionType.PARTY)));

    SharedWeekPage page = SharedWeekPage.of(week, messages);

    assertThat(page.occasions())
        .containsExactly(new SharedWeekPage.OccasionLine("Anna's party", "Friday, Nov 6", "Party"));
  }

  @Test
  void thePublicPageHoldsNoNotes() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    SharedWeek week =
        new SharedWeek(
            MONDAY, List.of(new SharedMeal(MONDAY, SlotType.LUNCH, 0, "Stew")), List.of());

    SharedWeekPage page = SharedWeekPage.of(week, messages);

    assertThat(page.days().get(0).slots().get(0).notes()).isEmpty();
  }

  @Test
  void thePrintPageKeepsOneNoteBesideEachMealWithAnEmptyTextForNone() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    List<SharedWeekPage.PrintMeal> meals =
        List.of(
            new SharedWeekPage.PrintMeal(MONDAY, SlotType.LUNCH, "Stew", "Less salt"),
            new SharedWeekPage.PrintMeal(MONDAY, SlotType.LUNCH, "Bread", null),
            new SharedWeekPage.PrintMeal(MONDAY, SlotType.DINNER, "Soup", "Warm"));

    SharedWeekPage page =
        SharedWeekPage.forPrint(
            MONDAY,
            meals,
            List.of(new SharedOccasion(MONDAY.plusDays(1), "Party", OccasionType.PARTY)),
            messages);

    List<SharedDay.Slot> slots = page.days().get(0).slots();
    assertThat(slots).hasSize(2);
    assertThat(slots.get(0).meals()).containsExactly("Stew", "Bread");
    assertThat(slots.get(0).notes()).containsExactly("Less salt", "");
    assertThat(slots.get(1).notes()).containsExactly("Warm");
    assertThat(page.occasions()).hasSize(1);
    assertThat(page.title()).isEqualTo("Week 45");
  }
}
