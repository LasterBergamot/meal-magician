package hu.lasterbergamot.mealmagician.sharing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A read-only link to one ISO week of a household's plan (PLAN.md D28). Only the SHA-256 hash of
 * the token is stored, so reading the table does not give anyone a working link.
 *
 * <p>{@code revokedAt} is only ever set by the guarded update in {@link ShareLinkRepository}, never
 * by setting a field and saving, so two members revoking at the same moment both succeed. There is
 * no version column and no "last viewed" column: opening a link must not write anything.
 */
@Entity
@Table(name = "share_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLink {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(name = "token_hash", nullable = false, updatable = false)
  private String tokenHash;

  @Column(name = "week_start", nullable = false, updatable = false)
  private LocalDate weekStart;

  // Null after the creator's account is deleted (ON DELETE SET NULL).
  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at", insertable = false, updatable = false)
  private Instant revokedAt;

  /**
   * Creates an active share link.
   *
   * @param householdId the household whose week is shared
   * @param tokenHash the SHA-256 hex of the token
   * @param weekStart the Monday of the shared week
   * @param createdBy the {@code app_user} id of the member who made the link
   * @param createdAt when the link was made
   * @param expiresAt when the link stops working; must be after {@code createdAt}
   */
  public ShareLink(
      long householdId,
      String tokenHash,
      LocalDate weekStart,
      long createdBy,
      Instant createdAt,
      Instant expiresAt) {
    this.householdId = householdId;
    this.tokenHash = tokenHash;
    this.weekStart = weekStart;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }
}
