package hu.lasterbergamot.mealmagician.assistant.internal;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for drafts. Every read and write that targets one draft takes the household id, so a
 * draft of another household is simply not found.
 */
public interface DraftRepository extends JpaRepository<DraftEntity, Long> {

  /**
   * Reads one draft of a household.
   *
   * @param id the draft
   * @param householdId the household
   * @return the draft, or empty if it does not exist in this household
   */
  Optional<DraftEntity> findByIdAndHouseholdId(long id, long householdId);

  /**
   * Moves a draft from OPEN to ACCEPTED in one conditional statement. Run it first in the accept
   * transaction: of two simultaneous accepts only one gets 1 back.
   *
   * @param id the draft
   * @param householdId the household
   * @return 1 if the draft was open and is now accepted, 0 if it was not open (or not found)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update DraftEntity d
      set d.status = hu.lasterbergamot.mealmagician.assistant.DraftStatus.ACCEPTED,
          d.version = d.version + 1
      where d.id = :id and d.householdId = :householdId
        and d.status = hu.lasterbergamot.mealmagician.assistant.DraftStatus.OPEN
      """)
  int claim(long id, long householdId);

  /**
   * Deletes one draft of a household; its items go with it (the foreign key cascades).
   *
   * @param id the draft
   * @param householdId the household
   * @return 1 if a draft was deleted, 0 if there was none
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from DraftEntity d where d.id = :id and d.householdId = :householdId")
  int deleteOne(long id, long householdId);

  /**
   * Deletes every draft created before a moment. This is a retention rule that reads no draft and
   * returns no data, which is why it is not scoped to a household: a household that never makes
   * another draft must not keep its old ones forever.
   *
   * @param cutoff drafts created before this are deleted
   * @return how many drafts were deleted
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from DraftEntity d where d.createdAt < :cutoff")
  int deleteCreatedBefore(Instant cutoff);
}
