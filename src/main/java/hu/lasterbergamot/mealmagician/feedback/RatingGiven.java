package hu.lasterbergamot.mealmagician.feedback;

import java.time.LocalDate;

/**
 * A member rated a meal, or changed their rating: one event per give, for a new rating and for an
 * update alike. The later assistant listens for it to learn what the household likes. The comment
 * is not part of the event, so personal text does not travel to listeners that do not need it.
 *
 * <p>Published inside the same transaction as the change, so a listener that must see only
 * committed data should use {@code @ApplicationModuleListener}, which runs after the commit.
 *
 * @param householdId the household
 * @param ratingId the rating's id
 * @param slotId the slot that was rated
 * @param mealId the meal that was planned there
 * @param userId the author
 * @param date the day of the slot
 * @param thumb up or down
 * @param stars 1 to 5, or {@code null} when none were given
 */
public record RatingGiven(
    long householdId,
    long ratingId,
    long slotId,
    long mealId,
    long userId,
    LocalDate date,
    Thumb thumb,
    Integer stars) {}
