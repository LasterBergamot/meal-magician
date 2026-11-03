package hu.lasterbergamot.mealmagician.sharing;

import java.time.Duration;

/**
 * How long a share link stays valid (PLAN.md D28). There is no "never": a link that ends up in the
 * wrong chat must stop working by itself.
 */
public enum ShareExpiry {
  /** One day. */
  DAY_1("1d", Duration.ofDays(1)),
  /** Seven days: the default. */
  DAYS_7("7d", Duration.ofDays(7)),
  /** Thirty days: the longest a link may live. */
  DAYS_30("30d", Duration.ofDays(30));

  private final String code;
  private final Duration duration;

  ShareExpiry(String code, Duration duration) {
    this.code = code;
    this.duration = duration;
  }

  /**
   * The value the share form posts for this choice.
   *
   * @return a short, stable code such as {@code 7d}
   */
  public String code() {
    return code;
  }

  /**
   * The lifetime this choice stands for.
   *
   * @return the duration from creation to expiry
   */
  public Duration duration() {
    return duration;
  }

  /**
   * Reads the form value back. Anything unknown (a tampered or missing value) gives the default
   * rather than an error, because the lifetime is capped at 30 days whatever is posted and an error
   * here would only confuse.
   *
   * @param code the posted value, may be {@code null}
   * @return the matching choice, or {@link #DAYS_7}
   */
  public static ShareExpiry fromCode(String code) {
    for (ShareExpiry expiry : values()) {
      if (expiry.code.equals(code)) {
        return expiry;
      }
    }
    return DAYS_7;
  }
}
