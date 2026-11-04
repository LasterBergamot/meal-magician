package hu.lasterbergamot.mealmagician.assistant.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the test can move, in the household's time zone. */
final class MutableClock extends Clock {

  private final ZoneId zone;
  private Instant now;

  MutableClock(Instant start, ZoneId zone) {
    this.now = start;
    this.zone = zone;
  }

  void set(Instant instant) {
    this.now = instant;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId newZone) {
    return new MutableClock(now, newZone);
  }

  @Override
  public Instant instant() {
    return now;
  }
}
