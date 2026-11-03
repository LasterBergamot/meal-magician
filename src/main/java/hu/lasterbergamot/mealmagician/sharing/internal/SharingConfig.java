package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.shared.security.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the sharing module's settings and the throttle of the public share page. The limiter is its
 * own bean, rather than a field in the controller, so a test can replace it and so Phase 6 can add
 * a second instance for invites without touching this one.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SharingProperties.class)
class SharingConfig {

  /**
   * The one global throttle of {@code GET /share/{token}}: {@code mm.share.rate-per-minute}
   * requests in each one-minute window.
   *
   * @param properties the settings
   * @param clock the application clock
   * @return the limiter
   */
  @Bean
  RateLimiter shareRateLimiter(SharingProperties properties, Clock clock) {
    return new RateLimiter(clock, properties.ratePerMinute(), Duration.ofMinutes(1));
  }
}
