package hu.lasterbergamot.mealmagician.household;

import java.time.Duration;

/**
 * How long an invite link stays valid (PLAN.md D9). A short life limits the damage if a link ends
 * up in the wrong chat.
 */
public enum InviteExpiry {
  /** Two hours: for when the owner is sitting next to the invited person. */
  HOURS_2("2h", Duration.ofHours(2)),
  /** One day: the default. */
  HOURS_24("24h", Duration.ofHours(24)),
  /** Three days: for people who answer slowly. */
  DAYS_3("3d", Duration.ofDays(3));

  private final String code;
  private final Duration duration;

  InviteExpiry(String code, Duration duration) {
    this.code = code;
    this.duration = duration;
  }

  /**
   * The value the invite form posts for this choice.
   *
   * @return a short, stable code such as {@code 24h}
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
   * rather than an error, because a longer-lived link than intended is bounded by the other limits
   * and an error here would only confuse.
   *
   * @param code the posted value, may be {@code null}
   * @return the matching choice, or {@link #HOURS_24}
   */
  public static InviteExpiry fromCode(String code) {
    for (InviteExpiry expiry : values()) {
      if (expiry.code.equals(code)) {
        return expiry;
      }
    }
    return HOURS_24;
  }
}
