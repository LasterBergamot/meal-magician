package hu.lasterbergamot.mealmagician.sharing.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for share links. "Active" is decided inside the queries (not revoked, not expired),
 * so no caller can forget to check it, and the revoke is one guarded {@code UPDATE}.
 */
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

  /**
   * The public lookup: the link a token points at, but only while it is active.
   *
   * @param tokenHash the SHA-256 hex of the token from the URL
   * @param now the current time
   * @return the link, or empty if no link has that hash or it is revoked or expired
   */
  @Query(
      """
      select s from ShareLink s
      where s.tokenHash = :tokenHash and s.revokedAt is null and s.expiresAt > :now
      """)
  Optional<ShareLink> findActiveByTokenHash(String tokenHash, Instant now);

  /**
   * Tenant-scoped lookup: a link id from another household finds nothing.
   *
   * @param id the link id
   * @param householdId the household the caller belongs to
   * @return the link, or empty if it does not exist or belongs to another household
   */
  Optional<ShareLink> findByIdAndHouseholdId(long id, long householdId);

  /**
   * The links made for one week, newest first.
   *
   * @param householdId the household
   * @param weekStart the Monday of the week
   * @return the links, including revoked and expired ones; the id breaks ties so the order is
   *     stable
   */
  List<ShareLink> findByHouseholdIdAndWeekStartOrderByCreatedAtDescIdDesc(
      long householdId, LocalDate weekStart);

  /**
   * Counts the links that can still be opened, for the per-household limit.
   *
   * @param householdId the household
   * @param now the current time
   * @return the number of links that are not revoked and expire after {@code now}
   */
  long countByHouseholdIdAndRevokedAtIsNullAndExpiresAtAfter(long householdId, Instant now);

  /**
   * Revokes a link, but only if it is not revoked yet. Revoking twice is harmless, which is why
   * callers ignore the count.
   *
   * @param id the link id
   * @param householdId the household the caller belongs to, so another household's link is
   *     untouched
   * @param now the current time
   * @return {@code 1} if this call revoked it, {@code 0} if it was already revoked
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update ShareLink s set s.revokedAt = :now
      where s.id = :id and s.householdId = :householdId and s.revokedAt is null
      """)
  int revoke(long id, long householdId, Instant now);

  /**
   * Deletes a household's old links, so the table does not grow forever without a scheduler.
   *
   * @param householdId the household
   * @param cutoff links that expired before this instant are deleted
   * @return the number of rows deleted
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ShareLink s where s.householdId = :householdId and s.expiresAt < :cutoff")
  int deleteByHouseholdIdAndExpiresAtBefore(long householdId, Instant cutoff);
}
