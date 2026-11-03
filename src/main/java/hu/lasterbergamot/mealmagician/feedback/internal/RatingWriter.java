package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.RatingGiven;
import hu.lasterbergamot.mealmagician.feedback.RatingInput;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one rating in one short transaction of its own. It exists as a separate bean so that
 * {@code RatingService.give} can retry it: on Postgres a failed statement aborts the whole
 * transaction, so a lost race (two tabs giving at once, the unique key on slot and user refuses the
 * second insert) can only be recovered by starting a new transaction, which then finds the winner's
 * row and updates it. A call on the same bean would not pass through the transaction proxy.
 */
@Component
public class RatingWriter {

  private final RatingRepository ratings;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Creates the writer.
   *
   * @param ratings persistence for ratings
   * @param events publishes {@link RatingGiven} inside the transaction
   * @param clock the application clock, for the timestamps
   */
  public RatingWriter(RatingRepository ratings, ApplicationEventPublisher events, Clock clock) {
    this.ratings = ratings;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Inserts the member's rating of the slot, or updates it if it exists, and publishes {@link
   * RatingGiven}. Joins a transaction that is already running (tests do that); otherwise it opens
   * its own. The insert is flushed at once, so a lost race surfaces here and not later at commit.
   *
   * @param userId the author
   * @param householdId the household, already checked
   * @param slot the slot as the planner shows it now, already checked to be in the household
   * @param clean the validated input
   * @return the saved rating
   * @throws org.springframework.dao.DataIntegrityViolationException if another transaction inserted
   *     the same member's rating of the same slot first; the caller retries
   */
  @Transactional
  public Rating upsert(long userId, long householdId, SlotView slot, RatingInput clean) {
    var now = clock.instant();
    Rating rating =
        ratings
            .findBySlotIdAndUserIdAndHouseholdId(slot.id(), userId, householdId)
            .map(found -> {
              found.update(
                  slot.mealId(),
                  slot.date(),
                  slot.slotType(),
                  clean.thumb(),
                  clean.stars(),
                  clean.comment(),
                  now);
              return found;
            })
            .orElseGet(() ->
                ratings.saveAndFlush(
                    new Rating(
                        householdId,
                        slot.id(),
                        slot.mealId(),
                        slot.date(),
                        slot.slotType(),
                        userId,
                        clean.thumb(),
                        clean.stars(),
                        clean.comment(),
                        now)));
    events.publishEvent(
        new RatingGiven(
            householdId,
            rating.getId(),
            slot.id(),
            slot.mealId(),
            userId,
            slot.date(),
            clean.thumb(),
            clean.stars()));
    return rating;
  }
}
