package hu.lasterbergamot.mealmagician.feedback;

/**
 * A member took their rating back. Listeners that kept what a {@link RatingGiven} told them drop
 * it. Published inside the same transaction as the delete, and only when a rating was really
 * removed.
 *
 * @param householdId the household
 * @param slotId the slot whose rating was withdrawn; the slot itself may be gone
 * @param userId the author who withdrew it
 */
public record RatingWithdrawn(long householdId, long slotId, long userId) {}
