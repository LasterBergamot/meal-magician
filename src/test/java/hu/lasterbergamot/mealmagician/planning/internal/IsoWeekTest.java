package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IsoWeekTest {

  @ParameterizedTest
  @CsvSource({"2026-W45, 2026-11-02", "2026-W01, 2025-12-29", "2026-W53, 2026-12-28"})
  void aWeekCodeGivesItsMonday(String code, LocalDate monday) {
    assertThat(IsoWeek.parse(code)).contains(new IsoWeek(monday));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"2021-W53", "2026-W00", "2026-W54", "2026-45", "2026-W5", "abc", ""})
  void anythingThatIsNotARealWeekIsEmpty(String code) {
    assertThat(IsoWeek.parse(code)).isEmpty();
  }

  @Test
  void aWeekCodeWithTrailingTextIsEmpty() {
    assertThat(IsoWeek.parse("2026-W45\n")).isEmpty();
    assertThat(IsoWeek.parse("2026-W45x")).isEmpty();
  }

  @Test
  void theFirstDayOfAYearCanBelongToTheLastWeekOfThePreviousOne() {
    assertThat(IsoWeek.containing(LocalDate.of(2027, 1, 1)).code()).isEqualTo("2026-W53");
  }

  @Test
  void theLastDaysOfDecemberCanBelongToWeekOneOfTheNextYear() {
    assertThat(IsoWeek.containing(LocalDate.of(2025, 12, 31)).code()).isEqualTo("2026-W01");
  }

  @Test
  void aMidWeekDayBelongsToTheWeekOfItsMonday() {
    IsoWeek week = IsoWeek.containing(LocalDate.of(2026, 11, 5));
    assertThat(week.monday()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(week.number()).isEqualTo(45);
    assertThat(week.sunday()).isEqualTo(LocalDate.of(2026, 11, 8));
  }

  @Test
  void stepsCrossTheYearBoundary() {
    assertThat(IsoWeek.parse("2026-W01").orElseThrow().previous().code()).isEqualTo("2025-W52");
    assertThat(IsoWeek.parse("2026-W53").orElseThrow().next().code()).isEqualTo("2027-W01");
  }

  @Test
  void aCodeReadsBackToTheSameWeek() {
    IsoWeek week = IsoWeek.containing(LocalDate.of(2026, 7, 15));
    assertThat(IsoWeek.parse(week.code())).contains(week);
  }

  @Test
  void containsIncludesMondayAndSundayButNothingOutside() {
    IsoWeek week = new IsoWeek(LocalDate.of(2026, 11, 2));
    assertThat(week.contains(LocalDate.of(2026, 11, 1))).isFalse();
    assertThat(week.contains(LocalDate.of(2026, 11, 2))).isTrue();
    assertThat(week.contains(LocalDate.of(2026, 11, 8))).isTrue();
    assertThat(week.contains(LocalDate.of(2026, 11, 9))).isFalse();
  }

  @Test
  void aWeekStartsOnAMonday() {
    assertThatIllegalArgumentException().isThrownBy(() -> new IsoWeek(LocalDate.of(2026, 11, 3)));
  }

  @Test
  void daysAreSevenStartingOnMonday() {
    assertThat(new IsoWeek(LocalDate.of(2026, 11, 2)).days())
        .hasSize(7)
        .first()
        .isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(new IsoWeek(LocalDate.of(2026, 11, 2)).days().get(6).getDayOfWeek())
        .isEqualTo(DayOfWeek.SUNDAY);
  }
}
