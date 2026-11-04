package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence for draft items. Every method takes the household id as well as the draft id. */
public interface DraftItemRepository extends JpaRepository<DraftItemEntity, Long> {

  /**
   * All items of a draft, in no particular order (the caller sorts by day, slot type and position).
   *
   * @param draftId the draft
   * @param householdId the household
   * @return the items; empty if the draft has none
   */
  List<DraftItemEntity> findByDraftIdAndHouseholdId(long draftId, long householdId);

  /**
   * One item of a draft.
   *
   * @param id the item
   * @param draftId the draft
   * @param householdId the household
   * @return the item, or empty if it is not in this draft of this household
   */
  Optional<DraftItemEntity> findByIdAndDraftIdAndHouseholdId(
      long id, long draftId, long householdId);

  /**
   * Deletes one item of a draft.
   *
   * @param id the item
   * @param draftId the draft
   * @param householdId the household
   * @return 1 if an item was deleted, 0 if there was none
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "delete from DraftItemEntity i"
          + " where i.id = :id and i.draftId = :draftId and i.householdId = :householdId")
  int deleteOne(long id, long draftId, long householdId);

  /**
   * Deletes all items of a draft, before a refined proposal is stored in their place.
   *
   * @param draftId the draft
   * @param householdId the household
   * @return how many items were deleted
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "delete from DraftItemEntity i where i.draftId = :draftId and i.householdId = :householdId")
  int deleteAll(long draftId, long householdId);
}
