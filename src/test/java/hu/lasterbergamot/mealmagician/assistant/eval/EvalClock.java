package hu.lasterbergamot.mealmagician.assistant.eval;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * A clock the harness moves from case to case, so "today" is the day a case says (a January week, a
 * Christmas week) while the assistant, the spend ledger and the planner all read the same time.
 * Without it the seasonal and occasion cases would depend on the day the eval happens to run.
 */
final class EvalClock extends Clock {

  private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
  private static final LocalTime MORNING = LocalTime.of(9, 0);

  private volatile Instant now = LocalDate.of(2026, 11, 2).atTime(MORNING).atZone(ZONE).toInstant();

  /**
   * Sets "today".
   *
   * @param date the day; the time is nine in the morning, Budapest time
   */
  void setDate(LocalDate date) {
    now = date.atTime(MORNING).atZone(ZONE).toInstant();
  }

  @Override
  public ZoneId getZone() {
    return ZONE;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return Clock.fixed(now, zone);
  }

  @Override
  public Instant instant() {
    return now;
  }
}
