package hu.lasterbergamot.mealmagician.household.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for invites. The two state changes are single guarded {@code UPDATE} statements that
 * report how many rows they changed: that count, not a read-then-write, is what makes "an invite
 * works once" hold when two requests arrive together.
 */
public interface InviteRepository extends JpaRepository<Invite, Long> {

  /**
   * Finds the invite a join link points at.
   *
   * @param codeHash the SHA-256 hex of the code from the link
   * @return the invite, or empty if no invite has that code
   */
  Optional<Invite> findByCodeHash(String codeHash);

  /**
   * The owner's invite list, newest first.
   *
   * @param householdId the household whose invites to list
   * @return its invites, newest first; the id breaks ties so the order is stable
   */
  List<Invite> findByHouseholdIdOrderByCreatedAtDescIdDesc(long householdId);

  /**
   * Tenant-scoped lookup: an invite id from another household finds nothing.
   *
   * @param id the invite id
   * @param householdId the household the caller owns
   * @return the invite, or empty if it does not exist or belongs to another household
   */
  Optional<Invite> findByIdAndHouseholdId(long id, long householdId);

  /**
   * Marks an invite as used, but only if it is still pending.
   *
   * @param id the invite id
   * @param userId the {@code app_user} id of the person joining
   * @param now the current time
   * @return {@code 1} if this call used it, {@code 0} if it was already used, revoked or expired
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Invite i set i.usedAt = :now, i.usedBy = :userId
      where i.id = :id and i.usedAt is null and i.revokedAt is null and i.expiresAt > :now
      """)
  int markUsed(long id, long userId, Instant now);

  /**
   * Revokes an invite, but only if it is still pending.
   *
   * @param id the invite id
   * @param householdId the household the caller owns, so another household's invite is untouched
   * @param now the current time
   * @return {@code 1} if this call revoked it, {@code 0} if it was already used or revoked
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Invite i set i.revokedAt = :now
      where i.id = :id and i.householdId = :householdId
        and i.usedAt is null and i.revokedAt is null
      """)
  int markRevoked(long id, long householdId, Instant now);
}
