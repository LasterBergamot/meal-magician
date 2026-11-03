package hu.lasterbergamot.mealmagician.feedback;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One rating as a page shows it. It carries the author's display name and never an e-mail address:
 * every member sees every rating, but the address stays private (D27).
 *
 * @param id the rating's id
 * @param slotId the slot that was rated; the slot may be gone by now
 * @param mealId the meal that was planned there
 * @param date the day of the slot, copied so it survives the slot
 * @param slotType the slot type, copied for the same reason
 * @param userId the author's {@code app_user} id
 * @param authorName the author's display name
 * @param thumb up or down
 * @param stars 1 to 5, or {@code null} when the author gave none
 * @param comment the cleaned comment, or {@code null} when there is none
 * @param mine whether the person who asked is the author, so a page can offer edit and clear
 * @param updatedAt when the rating was last given or changed
 */
public record RatingView(
    long id,
    long slotId,
    long mealId,
    LocalDate date,
    SlotType slotType,
    long userId,
    String authorName,
    Thumb thumb,
    Integer stars,
    String comment,
    boolean mine,
    Instant updatedAt) {}
