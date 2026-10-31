package hu.lasterbergamot.mealmagician.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One address that may sign in. Instance admins named in {@code MM_ADMIN_EMAILS} do not need a row.
 * The address is stored lower-case (see {@link Emails#normalize(String)}), so lookups are
 * case-insensitive.
 */
@Entity
@Table(name = "allowed_email")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllowedEmail {

  @Id private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AllowedEmailSource source;

  @Column(name = "added_by")
  private Long addedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /**
   * Creates an allowlist entry.
   *
   * @param email the address, already normalized
   * @param source why it is allowed
   * @param addedBy the {@code app_user} id that added it, or {@code null}
   * @param createdAt when it was added
   */
  @Builder
  public AllowedEmail(String email, AllowedEmailSource source, Long addedBy, Instant createdAt) {
    this.email = email;
    this.source = source;
    this.addedBy = addedBy;
    this.createdAt = createdAt;
  }
}
