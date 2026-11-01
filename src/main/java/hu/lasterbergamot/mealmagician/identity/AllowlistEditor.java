package hu.lasterbergamot.mealmagician.identity;

import hu.lasterbergamot.mealmagician.identity.internal.AllowedEmail;
import hu.lasterbergamot.mealmagician.identity.internal.AllowedEmailRepository;
import hu.lasterbergamot.mealmagician.identity.internal.AllowedEmailSource;
import hu.lasterbergamot.mealmagician.identity.internal.Emails;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The part of the allowlist other modules may change. Today that is one thing: when a household
 * owner invites an address, the address must be allowed to sign in (PLAN.md D9), otherwise the
 * invited person would be turned away by the allowlist before they could ever open the invite link.
 *
 * <p>Who may <em>read</em> the allowlist stays inside this module; sign-in is the only reader.
 */
@Service
public class AllowlistEditor {

  private final AllowedEmailRepository allowedEmails;
  private final Clock clock;

  /**
   * Creates the editor.
   *
   * @param allowedEmails persistence for the allowlist
   * @param clock the application clock
   */
  public AllowlistEditor(AllowedEmailRepository allowedEmails, Clock clock) {
    this.allowedEmails = allowedEmails;
    this.clock = clock;
  }

  /**
   * Brings an address into the form the allowlist stores and compares: trimmed and lower-case.
   * Other modules use it so their own comparisons agree with the sign-in check.
   *
   * @param email the raw address, may be {@code null}
   * @return the normalized address, or an empty string for {@code null}
   */
  public String normalize(String email) {
    return Emails.normalize(email);
  }

  /**
   * Lets an invited address sign in. An address that is already on the list keeps its existing row
   * (and so its source), because an admin's entry must not be turned into an invite entry.
   *
   * @param email the invited address, in any case
   * @param invitedBy the {@code app_user} id of the household owner who invited it
   */
  @Transactional
  public void allowInvitedAddress(String email, long invitedBy) {
    String normalized = Emails.normalize(email);
    if (!allowedEmails.existsById(normalized)) {
      allowedEmails.save(
          AllowedEmail.builder()
              .email(normalized)
              .source(AllowedEmailSource.INVITE)
              .addedBy(invitedBy)
              .createdAt(Instant.now(clock))
              .build());
    }
  }
}
