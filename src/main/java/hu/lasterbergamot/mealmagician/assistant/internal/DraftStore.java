package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.DraftClosedException;
import hu.lasterbergamot.mealmagician.assistant.DraftNotFoundException;
import hu.lasterbergamot.mealmagician.assistant.DraftStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a new or refined draft with its items in one transaction. It exists apart from {@link
 * DraftService} so that the model call, which can take many seconds, is made <em>outside</em> any
 * transaction and only this short write is inside one (a transaction must not stay open across a
 * model call).
 *
 * <p>Nothing here checks membership; {@link DraftService} did that before calling.
 */
@Component
public class DraftStore {

  /** How long a draft is kept, counted from its creation. */
  static final Duration RETENTION = Duration.ofDays(7);

  private final DraftRepository drafts;
  private final DraftItemRepository items;
  private final Clock clock;

  /**
   * Creates the store.
   *
   * @param drafts persistence for drafts
   * @param items persistence for draft items
   * @param clock the application clock
   */
  public DraftStore(DraftRepository drafts, DraftItemRepository items, Clock clock) {
    this.drafts = drafts;
    this.items = items;
    this.clock = clock;
  }

  /**
   * Stores a new open draft. First it deletes every draft older than seven days (any household's:
   * this is the retention rule, there is no scheduler), so the table cannot grow without bound
   * while the app is used.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id of the person who asked
   * @param monday the Monday of the week
   * @param request what the member asked
   * @param conversation the turns so far as JSON
   * @param summary the cleaned summary, or {@code null}
   * @param proposed the checked items
   * @return the saved draft
   */
  @Transactional
  public DraftEntity create(
      long householdId,
      long userId,
      LocalDate monday,
      String request,
      String conversation,
      String summary,
      List<DraftValidator.Item> proposed) {
    Instant now = clock.instant();
    drafts.deleteCreatedBefore(now.minus(RETENTION));
    DraftEntity draft =
        drafts.save(
            new DraftEntity(householdId, userId, monday, request, conversation, summary, now));
    saveItems(draft, proposed);
    return draft;
  }

  /**
   * Replaces a draft's items, conversation and summary after a follow-up.
   *
   * @param householdId the household
   * @param draftId the draft
   * @param conversation the turns so far, including the new ones, as JSON
   * @param summary the cleaned summary, or {@code null}
   * @param proposed the checked items, which replace all the old ones
   * @return the saved draft
   * @throws DraftNotFoundException if the draft is gone (for example purged during the model call)
   * @throws DraftClosedException if it was accepted while the model was working
   */
  @Transactional
  public DraftEntity replace(
      long householdId,
      long draftId,
      String conversation,
      String summary,
      List<DraftValidator.Item> proposed) {
    DraftEntity draft =
        drafts
            .findByIdAndHouseholdId(draftId, householdId)
            .orElseThrow(() -> new DraftNotFoundException(draftId));
    if (draft.getStatus() != DraftStatus.OPEN) {
      throw new DraftClosedException(draftId);
    }
    draft.refined(conversation, summary);
    drafts.saveAndFlush(draft);
    items.deleteAll(draftId, householdId);
    saveItems(draft, proposed);
    return draft;
  }

  private void saveItems(DraftEntity draft, List<DraftValidator.Item> proposed) {
    items.saveAll(
        proposed.stream()
            .map(item ->
                item.mealId() != null
                    ? DraftItemEntity.library(
                        draft.getId(),
                        draft.getHouseholdId(),
                        item.date(),
                        item.slotType(),
                        item.position(),
                        item.mealId())
                    : DraftItemEntity.idea(
                        draft.getId(),
                        draft.getHouseholdId(),
                        item.date(),
                        item.slotType(),
                        item.position(),
                        item.name(),
                        item.reason(),
                        item.tags(),
                        item.ingredients()))
            .toList());
  }
}
