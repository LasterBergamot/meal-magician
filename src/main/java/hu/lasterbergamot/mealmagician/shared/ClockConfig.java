package hu.lasterbergamot.mealmagician.shared;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application's {@link Clock}.
 *
 * <p>Code must ask this clock for the current time instead of calling {@code LocalDate.now()}. That
 * keeps "today" in the household's time zone (the server may run in UTC) and lets tests replace the
 * clock with a fixed one.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  /** The household's time zone; weeks are planned in local time (PLAN.md D14). */
  public static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

  /**
   * Creates the system clock fixed to {@link #ZONE}.
   *
   * @return a clock that reports the current time in Europe/Budapest
   */
  @Bean
  public Clock clock() {
    return Clock.system(ZONE);
  }
}
