package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PlanPageModelsTest {

  private static final Locale HU = Locale.forLanguageTag("hu");
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private static SlotGroup empty(LocalDate date, SlotType type) {
    return new SlotGroup(date, type, List.of());
  }

  private static DayPlan day(LocalDate date) {
    return new DayPlan(
        date,
        "name",
        "label",
        "n",
        "full",
        false,
        Arrays.stream(SlotType.values()).map(type -> empty(date, type)).toList());
  }

  private static WeekPage week() {
    IsoWeek week = new IsoWeek(MONDAY);
    return new WeekPage(
        week, "45. hét", "range", true, week.days().stream().map(PlanPageModelsTest::day).toList());
  }

  @Test
  void weekPageBuildsTheAddressesOfThisAndNeighbouringWeeks() {
    WeekPage page = week();
    assertThat(WeekPage.hrefOf(new IsoWeek(MONDAY))).isEqualTo("/plan?week=2026-W45");
    assertThat(page.href()).isEqualTo("/plan?week=2026-W45");
    assertThat(page.previousHref()).isEqualTo("/plan?week=2026-W44");
    assertThat(page.nextHref()).isEqualTo("/plan?week=2026-W46");
  }

  @Test
  void rowsAreOnePerSlotTypeWithSevenCellsEach() {
    List<SlotRow> rows = week().rows();
    assertThat(rows).extracting(SlotRow::type).containsExactly(SlotType.values());
    for (SlotRow row : rows) {
      assertThat(row.cells()).hasSize(7);
      assertThat(row.cells()).extracting(SlotGroup::type).containsOnly(row.type());
      assertThat(row.cells().get(0).date()).isEqualTo(MONDAY);
      assertThat(row.cells().get(6).date()).isEqualTo(MONDAY.plusDays(6));
    }
    assertThat(rows.get(2).slotName()).isEqualTo("DINNER");
    assertThat(rows.get(2).labelKey()).isEqualTo("slot.dinner");
  }

  @Test
  void aDayHasAnIdAndFindsItsGroupBySlotType() {
    DayPlan day = day(MONDAY);
    assertThat(DayPlan.idOf(MONDAY)).isEqualTo("day-2026-11-02");
    assertThat(day.id()).isEqualTo("day-2026-11-02");
    assertThat(day.dayOfMonth()).isEqualTo(2);
    assertThat(day.group(SlotType.LUNCH).type()).isEqualTo(SlotType.LUNCH);
  }

  @Test
  void aSlotGroupKnowsItsKeyLabelAndWhetherItIsEmpty() {
    SlotGroup empty = empty(MONDAY, SlotType.LUNCH);
    SlotGroup filled =
        new SlotGroup(
            MONDAY, SlotType.SNACK, List.of(new PlannedMeal(1, 2, "Alma", false, null, 0, 0)));
    assertThat(SlotGroup.labelKeyOf(SlotType.SNACK)).isEqualTo("slot.snack");
    assertThat(empty.labelKey()).isEqualTo("slot.lunch");
    assertThat(empty.slotName()).isEqualTo("LUNCH");
    assertThat(empty.key()).isEqualTo("2026-11-02/LUNCH");
    assertThat(empty.empty()).isTrue();
    assertThat(filled.empty()).isFalse();
  }

  @Test
  void datesAreFormattedInTheReadersLanguage() {
    assertThat(PlanDates.dayName(MONDAY, HU)).isEqualTo("Hétfő");
    assertThat(PlanDates.dayName(MONDAY, Locale.ENGLISH)).isEqualTo("Monday");
    assertThat(PlanDates.shortName(MONDAY, Locale.ENGLISH)).isEqualTo("Mon");
    assertThat(PlanDates.dateLabel(MONDAY, HU)).contains("nov");
    assertThat(PlanDates.dateLabel(MONDAY, Locale.ENGLISH)).contains("Nov");
    assertThat(PlanDates.mediumDate(MONDAY, HU)).contains("2026");
    assertThat(PlanDates.mediumDate(MONDAY, Locale.ENGLISH)).contains("2026");
    assertThat(PlanDates.fullLabel(MONDAY, Locale.ENGLISH)).contains("Monday");
  }
}
