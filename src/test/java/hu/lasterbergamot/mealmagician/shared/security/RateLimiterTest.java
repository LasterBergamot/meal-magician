package hu.lasterbergamot.mealmagician.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  /** A clock the test moves by hand. */
  private static final class MovableClock extends Clock {
    private volatile Instant now = Instant.parse("2026-11-03T10:00:00Z");

    void advance(Duration by) {
      now = now.plus(by);
    }

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
      return now;
    }
  }

  private final MovableClock clock = new MovableClock();
  private final RateLimiter limiter = new RateLimiter(clock, 3, Duration.ofMinutes(1));

  @Test
  void allowsCallsUpToTheLimitThenRefuses() {
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isTrue();

    assertThat(limiter.tryAcquire()).isFalse();
    assertThat(limiter.tryAcquire()).isFalse();
  }

  @Test
  void aNewWindowStartsWhenTheOldOneEnds() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire();
    }
    clock.advance(Duration.ofSeconds(60));

    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isFalse();
  }

  @Test
  void theLastInstantBeforeTheWindowEndsIsStillTheOldWindow() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire();
    }
    clock.advance(Duration.ofSeconds(59).plusMillis(999));

    assertThat(limiter.tryAcquire()).isFalse();
  }

  @Test
  void refusedCallsDoNotExtendTheBlock() {
    for (int i = 0; i < 5; i++) {
      limiter.tryAcquire();
    }
    clock.advance(Duration.ofSeconds(60));

    assertThat(limiter.tryAcquire()).isTrue();
  }

  @Test
  void retryAfterCountsWholeSecondsRoundedUpToTheEndOfTheWindow() {
    assertThat(limiter.retryAfterSeconds()).isEqualTo(60);
    clock.advance(Duration.ofMillis(10_500));
    assertThat(limiter.retryAfterSeconds()).isEqualTo(50);
  }

  @Test
  void retryAfterIsNeverLessThanOneSecond() {
    clock.advance(Duration.ofMinutes(5));

    assertThat(limiter.retryAfterSeconds()).isEqualTo(1);
  }

  @Test
  void aLimitOfOneAllowsExactlyOneCallPerWindow() {
    RateLimiter one = new RateLimiter(clock, 1, Duration.ofSeconds(10));

    assertThat(one.tryAcquire()).isTrue();
    assertThat(one.tryAcquire()).isFalse();
  }

  @Test
  void concurrentCallsNeverExceedTheLimit() throws Exception {
    RateLimiter shared = new RateLimiter(clock, 10, Duration.ofMinutes(1));
    int threads = 64;
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger allowed = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> done = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        done.add(
            pool.submit(() -> {
              go.await();
              if (shared.tryAcquire()) {
                allowed.incrementAndGet();
              }
              return null;
            }));
      }
      go.countDown();
      for (Future<?> result : done) {
        result.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(allowed).hasValue(10);
  }

  @Test
  void aLimitBelowOneIsRefused() {
    assertThatThrownBy(() -> new RateLimiter(clock, 0, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWindowThatIsNotPositiveIsRefused() {
    assertThatThrownBy(() -> new RateLimiter(clock, 1, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RateLimiter(clock, 1, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
