package hu.lasterbergamot.mealmagician.household.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An invitation to join a household, for one e-mail address (PLAN.md D9). Only the SHA-256 hash of
 * the code is stored, so reading the table does not give anyone a working link.
 *
 * <p>The three timestamps are only ever changed by the guarded updates in {@link InviteRepository},
 * never by setting a field and saving, so two people clicking at the same moment cannot both win.
 */
@Entity
@Table(name = "invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false, updatable = false)
  private String email;

  @Column(name = "code_hash", nullable = false, updatable = false)
  private String codeHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at", insertable = false, updatable = false)
  private Instant revokedAt;

  @Column(name = "used_at", insertable = false, updatable = false)
  private Instant usedAt;

  @Column(name = "used_by", insertable = false, updatable = false)
  private Long usedBy;

  @Column(name = "created_by", nullable = false, updatable = false)
  private long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /**
   * Creates a pending invite.
   *
   * @param householdId the household the person is invited to
   * @param email the invited address, already normalized
   * @param codeHash the SHA-256 hex of the code
   * @param expiresAt when the link stops working
   * @param createdBy the {@code app_user} id of the owner who invited
   * @param createdAt when the invite was made
   */
  public Invite(
      long householdId,
      String email,
      String codeHash,
      Instant expiresAt,
      long createdBy,
      Instant createdAt) {
    this.householdId = householdId;
    this.email = email;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }
}
