package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WeekCodesTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneId.of("Europe/Budapest"));
  private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 11, 2);

  @Test
  void aRealWeekCodeIsItsMonday() {
    assertThat(WeekCodes.mondayOf("2020-W20", CLOCK)).isEqualTo(LocalDate.of(2020, 5, 11));
    assertThat(WeekCodes.mondayOf("2020-W53", CLOCK)).isEqualTo(LocalDate.of(2020, 12, 28));
  }

  @Test
  void aMissingOrBrokenCodeMeansThisWeek() {
    assertThat(WeekCodes.mondayOf(null, CLOCK)).isEqualTo(THIS_MONDAY);
    assertThat(WeekCodes.mondayOf("next week", CLOCK)).isEqualTo(THIS_MONDAY);
  }

  @Test
  void aWeekNumberThatTheYearDoesNotHaveMeansThisWeek() {
    assertThat(WeekCodes.mondayOf("2026-W54", CLOCK)).isEqualTo(THIS_MONDAY);
    assertThat(WeekCodes.mondayOf("2026-W00", CLOCK)).isEqualTo(THIS_MONDAY);
    assertThat(WeekCodes.mondayOf("2026-W53", CLOCK)).isEqualTo(LocalDate.of(2026, 12, 28));
    assertThat(WeekCodes.mondayOf("2025-W53", CLOCK)).isEqualTo(THIS_MONDAY);
  }

  @Test
  void aCodeReadsBackAsTheSameWeek() {
    assertThat(WeekCodes.codeOf(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
    assertThat(WeekCodes.codeOf(THIS_MONDAY)).isEqualTo("2026-W45");
  }
}
