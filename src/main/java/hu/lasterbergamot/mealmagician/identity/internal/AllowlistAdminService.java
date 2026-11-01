package hu.lasterbergamot.mealmagician.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an instance admin does with the allowlist (PLAN.md D6): see it, add an address, remove one.
 *
 * <p>The service does not check who is asking. {@link SecurityConfig} only lets people with the
 * admin role reach {@code /admin/**}, and this class is package-private so nothing else can call
 * it. The list is the bootstrap admins from {@code MM_ADMIN_EMAILS} (shown, but fixed) plus the
 * rows of {@code allowed_email}.
 *
 * <p>Removing an address stops the person's <em>next</em> sign-in. A session they already have
 * keeps working until it expires (30 days), because sessions are not tied back to the allowlist.
 */
@Service
@Transactional
class AllowlistAdminService {

  private final IdentityProperties properties;
  private final AllowedEmailRepository allowedEmails;
  private final Clock clock;

  AllowlistAdminService(
      IdentityProperties properties, AllowedEmailRepository allowedEmails, Clock clock) {
    this.properties = properties;
    this.allowedEmails = allowedEmails;
    this.clock = clock;
  }

  /**
   * Lists everyone who may sign in.
   *
   * @return the bootstrap admins first, then the table's rows by address; an admin who also has a
   *     row appears once, as a bootstrap admin
   */
  @Transactional(readOnly = true)
  List<AllowlistEntry> list() {
    Set<String> bootstrap = properties.normalizedAdminEmails();
    var entries = new ArrayList<AllowlistEntry>();
    bootstrap.stream()
        .sorted()
        .forEach(email -> entries.add(new AllowlistEntry(email, AllowlistOrigin.BOOTSTRAP, null)));
    for (AllowedEmail row : allowedEmails.findAll(Sort.by("email"))) {
      if (!bootstrap.contains(row.getEmail())) {
        entries.add(
            new AllowlistEntry(
                row.getEmail(),
                row.getSource() == AllowedEmailSource.ADMIN
                    ? AllowlistOrigin.ADMIN
                    : AllowlistOrigin.INVITE,
                row.getCreatedAt()));
      }
    }
    return entries;
  }

  /**
   * Lets an address sign in.
   *
   * @param email the address, in any case
   * @param adminId the {@code app_user} id of the admin adding it
   * @throws AllowlistRefusedException with {@link AllowlistProblem#INVALID_EMAIL} if it is not
   *     shaped like an address, or {@link AllowlistProblem#ALREADY_ALLOWED} if it can already sign
   *     in
   */
  void add(String email, long adminId) {
    String normalized = Emails.normalize(email);
    if (!Emails.isValid(normalized)) {
      throw new AllowlistRefusedException(AllowlistProblem.INVALID_EMAIL);
    }
    if (properties.normalizedAdminEmails().contains(normalized)
        || allowedEmails.existsById(normalized)) {
      throw new AllowlistRefusedException(AllowlistProblem.ALREADY_ALLOWED);
    }
    allowedEmails.save(
        AllowedEmail.builder()
            .email(normalized)
            .source(AllowedEmailSource.ADMIN)
            .addedBy(adminId)
            .createdAt(Instant.now(clock))
            .build());
  }

  /**
   * Stops an address from signing in. Removing one that is not on the list does nothing, so a
   * double click is harmless.
   *
   * @param email the address, in any case
   * @throws AllowlistRefusedException with {@link AllowlistProblem#BOOTSTRAP_ADMIN} if the address
   *     is named in {@code MM_ADMIN_EMAILS}
   */
  void remove(String email) {
    String normalized = Emails.normalize(email);
    if (properties.normalizedAdminEmails().contains(normalized)) {
      throw new AllowlistRefusedException(AllowlistProblem.BOOTSTRAP_ADMIN);
    }
    allowedEmails.deleteById(normalized);
  }
}
