package hu.lasterbergamot.mealmagician.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.session.FindByIndexNameSessionRepository;
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
 * <p>Removing an address also ends that person's live sessions (#59), in the same transaction, so
 * they are sent to sign-in on their next request instead of keeping their 30 days. Sessions are
 * found by the principal name, which for Google sign-ins is the OIDC {@code sub}; the address is
 * mapped to its {@code app_user} rows first.
 */
@Service
@Transactional
class AllowlistAdminService {

  private final IdentityProperties properties;
  private final AllowedEmailRepository allowedEmails;
  private final AppUserRepository users;
  private final FindByIndexNameSessionRepository<?> sessions;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param properties the bootstrap admins from {@code MM_ADMIN_EMAILS}
   * @param allowedEmails persistence for the allowlist table
   * @param users persistence for users, to map an address to the Google account behind it
   * @param sessions the Spring Session store, which can find a session by its principal name and
   *     delete it; Spring Boot wires the JDBC store here
   * @param clock the application clock
   */
  AllowlistAdminService(
      IdentityProperties properties,
      AllowedEmailRepository allowedEmails,
      AppUserRepository users,
      FindByIndexNameSessionRepository<?> sessions,
      Clock clock) {
    this.properties = properties;
    this.allowedEmails = allowedEmails;
    this.users = users;
    this.sessions = sessions;
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
   * Stops an address from signing in and ends the live sessions of the person behind it (#59).
   * Removing one that is not on the list does nothing to the list, so a double click is harmless;
   * the sessions are ended anyway, which also cleans up after a removal made before this rule
   * existed.
   *
   * <p>Only sessions whose principal is the Google account of an {@code app_user} row with this
   * address are deleted; other people's sessions are never looked at. The deletes share the
   * transaction with the allowlist change, so either both happen or neither does.
   *
   * @param email the address, in any case
   * @throws AllowlistRefusedException with {@link AllowlistProblem#BOOTSTRAP_ADMIN} if the address
   *     is named in {@code MM_ADMIN_EMAILS}; nothing is removed or ended then, because a bootstrap
   *     admin keeps access
   */
  void remove(String email) {
    String normalized = Emails.normalize(email);
    if (properties.normalizedAdminEmails().contains(normalized)) {
      throw new AllowlistRefusedException(AllowlistProblem.BOOTSTRAP_ADMIN);
    }
    allowedEmails.deleteById(normalized);
    endSessionsOf(normalized);
  }

  /**
   * Deletes every session whose principal is a Google account that last signed in with this
   * address. An address without an {@code app_user} row has no sessions, so nothing happens.
   *
   * @param email the normalized address that was removed
   */
  private void endSessionsOf(String email) {
    for (AppUser user : users.findAllByEmail(email)) {
      for (String sessionId : sessions.findByPrincipalName(user.getGoogleSub()).keySet()) {
        sessions.deleteById(sessionId);
      }
    }
  }
}
