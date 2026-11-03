package hu.lasterbergamot.mealmagician.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import hu.lasterbergamot.mealmagician.shared.security.RateLimiter;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The global throttle of the public share page (PLAN.md D28): the request after the limit gets 429
 * with {@code Retry-After} and the privacy headers, and the next window is allowed again. A limiter
 * with a clock the test moves replaces the real one, so nothing here waits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(PublicShareThrottleTest.MovableLimiter.class)
class PublicShareThrottleTest {

  private static final int LIMIT = 3;
  private static final AtomicReference<Instant> NOW =
      new AtomicReference<>(Instant.parse("2026-11-03T10:00:00Z"));

  /** A limiter of {@value #LIMIT} a minute that reads the test's clock. */
  @TestConfiguration(proxyBeanMethods = false)
  static class MovableLimiter {
    @Bean
    @Primary
    RateLimiter testShareRateLimiter() {
      Clock clock =
          new Clock() {
            @Override
            public ZoneId getZone() {
              return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
              return this;
            }

            @Override
            public Instant instant() {
              return NOW.get();
            }
          };
      return new RateLimiter(clock, LIMIT, Duration.ofMinutes(1));
    }
  }

  @Autowired private MockMvc mvc;

  private MockHttpServletResponse open() throws Exception {
    return mvc.perform(get("/share/" + TokenCodes.generate())).andReturn().getResponse();
  }

  @Test
  void theRequestAfterTheLimitGets429AndTheNextWindowIsAllowed() throws Exception {
    for (int i = 0; i < LIMIT; i++) {
      assertThat(open().getStatus()).isEqualTo(404);
    }

    MockHttpServletResponse throttled = open();

    assertThat(throttled.getStatus()).isEqualTo(429);
    assertThat(throttled.getHeader("Retry-After")).isEqualTo("60");
    assertThat(throttled.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow, noarchive");
    assertThat(throttled.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(throttled.getHeader("Cache-Control")).contains("no-store");
    assertThat(throttled.getHeaders("Set-Cookie")).isEmpty();
    assertThat(throttled.getContentAsString()).contains("Túl sok megnyitás");

    NOW.set(NOW.get().plusSeconds(61));

    assertThat(open().getStatus()).isEqualTo(404);
  }
}
