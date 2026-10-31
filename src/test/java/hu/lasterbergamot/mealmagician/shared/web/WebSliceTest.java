package hu.lasterbergamot.mealmagician.shared.web;

import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import gg.jte.springframework.boot.autoconfigure.ServletJteAutoConfiguration;
import hu.lasterbergamot.mealmagician.shared.i18n.I18nConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Shared setup of the web slice tests: a {@code @WebMvcTest} does not load JTE or our configuration
 * on its own, so this meta-annotation adds them. Time is fixed to 2026-10-31 in Budapest, which was
 * a Saturday.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@WebMvcTest
@ImportAutoConfiguration({JteAutoConfiguration.class, ServletJteAutoConfiguration.class})
@Import({I18nConfig.class, WebSliceTest.FixedClock.class})
@interface WebSliceTest {

  @TestConfiguration(proxyBeanMethods = false)
  class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneId.of("Europe/Budapest"));
    }
  }
}
