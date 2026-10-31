package hu.lasterbergamot.mealmagician.identity.internal;

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
 * A person who has signed in with Google. Rows exist only for allowlisted accounts: a visitor who
 * is refused never gets one. The Google {@code sub} identifies the person; the e-mail and name are
 * refreshed at every sign-in because Google users can change them.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

  /** UI language for new users (PLAN.md D14). */
  public static final String DEFAULT_LOCALE = "hu";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "google_sub", nullable = false, updatable = false)
  private String googleSub;

  @Column(nullable = false)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String locale;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /**
   * Creates a user at their first sign-in.
   *
   * @param googleSub Google's stable account id
   * @param email the verified, normalized e-mail address
   * @param displayName the name to show
   * @param createdAt when they first signed in
   */
  public AppUser(String googleSub, String email, String displayName, Instant createdAt) {
    this.googleSub = googleSub;
    this.email = email;
    this.displayName = displayName;
    this.locale = DEFAULT_LOCALE;
    this.createdAt = createdAt;
  }

  /**
   * Refreshes the profile fields Google may have changed since the last sign-in.
   *
   * @param newEmail the verified, normalized e-mail address
   * @param newDisplayName the name to show
   */
  public void refreshProfile(String newEmail, String newDisplayName) {
    this.email = newEmail;
    this.displayName = newDisplayName;
  }
}
