package hu.lasterbergamot.mealmagician.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  @Test
  void clockRunsInTheHouseholdTimeZone() {
    Clock clock = new ClockConfig().clock();

    assertThat(clock.getZone()).isEqualTo(ClockConfig.ZONE);
    assertThat(ClockConfig.ZONE.getId()).isEqualTo("Europe/Budapest");
  }
}
