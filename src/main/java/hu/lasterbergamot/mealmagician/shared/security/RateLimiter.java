package hu.lasterbergamot.mealmagician.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A small, thread-safe fixed-window counter: at most {@code limit} calls to {@link #tryAcquire()}
 * succeed in each {@code window}, then the counter starts again.
 *
 * <p>It is deliberately global and simple (PLAN.md D28). It has no scheduler and no map, so it
 * never wakes a database or a timer thread, which keeps Cloud Run able to scale to zero (D11). It
 * keeps no per-client state because the client address is not reliable behind Cloud Run's proxy;
 * per-address limiting arrives with Phase 6's invite and join limiter, which can reuse this class
 * with its own instance. A fixed window allows a burst of up to twice the limit across a window
 * boundary; that is acceptable for a flood guard.
 *
 * <p>The state lives in this instance, so a restart or a second Cloud Run instance starts a fresh
 * window. That is fine for a guard against a flood, not a hard quota.
 */
public final class RateLimiter {

  private final Clock clock;
  private final int limit;
  private final Duration window;

  private Instant windowStart;
  private int used;

  /**
   * Creates a limiter whose first window starts now.
   *
   * @param clock the time source; tests pass a clock they can move
   * @param limit how many calls one window allows, at least 1
   * @param window how long a window lasts, longer than zero
   * @throws IllegalArgumentException if the limit is below 1 or the window is not positive
   */
  public RateLimiter(Clock clock, int limit, Duration window) {
    if (limit < 1) {
      throw new IllegalArgumentException("The limit must be at least 1: " + limit);
    }
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("The window must be longer than zero: " + window);
    }
    this.clock = clock;
    this.limit = limit;
    this.window = window;
    this.windowStart = clock.instant();
  }

  /**
   * Counts one call if the current window still has room.
   *
   * @return {@code true} if the call is allowed, {@code false} if the window is used up and the
   *     caller should answer 429; a refused call is not counted, so it does not extend the block
   */
  public synchronized boolean tryAcquire() {
    Instant now = clock.instant();
    if (!now.isBefore(windowStart.plus(window))) {
      windowStart = now;
      used = 0;
    }
    if (used >= limit) {
      return false;
    }
    used++;
    return true;
  }

  /**
   * How long a refused caller should wait: the value for a {@code Retry-After} header.
   *
   * @return whole seconds until the current window ends, never less than 1
   */
  public synchronized long retryAfterSeconds() {
    long millis = Duration.between(clock.instant(), windowStart.plus(window)).toMillis();
    return Math.max(1, Math.ceilDiv(millis, 1000L));
  }
}
