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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared setup of the web slice tests: a {@code @WebMvcTest} does not load JTE or our configuration
 * on its own, so this meta-annotation adds them. Time is fixed to 2026-10-31 in Budapest, which was
 * a Saturday.
 *
 * <p>The household module's web classes and the allowlist admin controller are left out: they need
 * their services and a database, and are tested end to end in {@code HouseholdPagesTest} and {@code
 * AllowlistAdminPagesTest}.
 *
 * <p>Spring Security is on the classpath, so a slice would otherwise get its default "everything
 * needs a login" chain. {@link OpenSecurity} lets every request through: these tests are about
 * templates and headers; who may enter is tested in {@code SecurityRulesTests}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@WebMvcTest(
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern =
                "hu\\.lasterbergamot\\.mealmagician\\.(household\\..*|identity\\.internal\\.AllowlistAdminController)"))
@ImportAutoConfiguration({JteAutoConfiguration.class, ServletJteAutoConfiguration.class})
@Import({I18nConfig.class, WebSliceTest.FixedClock.class, WebSliceTest.OpenSecurity.class})
@interface WebSliceTest {

  @TestConfiguration(proxyBeanMethods = false)
  class OpenSecurity {
    @Bean
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
          .csrf(Customizer.withDefaults())
          .build();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneId.of("Europe/Budapest"));
    }
  }
}
