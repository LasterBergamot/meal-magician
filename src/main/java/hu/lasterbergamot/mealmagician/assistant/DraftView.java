package hu.lasterbergamot.mealmagician.assistant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A draft as a page shows it. Texts the model or the member wrote ({@code summary}, item names,
 * {@code request}) are data: a page must escape them.
 *
 * @param id the draft's id
 * @param weekStart the Monday of the week the draft is for
 * @param status open or accepted
 * @param request what the member asked for the first time
 * @param summary the model's one-line summary, or {@code null}
 * @param items the proposed meals, ordered by day, slot type and position
 * @param createdAt when the draft was made
 */
public record DraftView(
    long id,
    LocalDate weekStart,
    DraftStatus status,
    String request,
    String summary,
    List<DraftItemView> items,
    Instant createdAt) {}
