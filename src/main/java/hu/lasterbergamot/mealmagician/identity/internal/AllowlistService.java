package hu.lasterbergamot.mealmagician.identity.internal;

import org.springframework.stereotype.Service;

/**
 * Decides who may sign in (PLAN.md D6): the bootstrap admins from {@code MM_ADMIN_EMAILS}, plus
 * every address in the {@code allowed_email} table.
 *
 * <p>Changing who has access never needs a redeploy for the table; only the admin list is
 * configuration.
 */
@Service
class AllowlistService {

  private final IdentityProperties properties;
  private final AllowedEmailRepository allowedEmails;

  AllowlistService(IdentityProperties properties, AllowedEmailRepository allowedEmails) {
    this.properties = properties;
    this.allowedEmails = allowedEmails;
  }

  /**
   * Whether the address is an instance admin.
   *
   * @param email the address as Google reported it
   * @return {@code true} if it is named in {@code MM_ADMIN_EMAILS}
   */
  boolean isAdmin(String email) {
    return properties.normalizedAdminEmails().contains(Emails.normalize(email));
  }

  /**
   * Whether the address may sign in.
   *
   * @param email the address as Google reported it
   * @return {@code true} for admins and for addresses on the allowlist table
   */
  boolean isAllowed(String email) {
    return isAdmin(email) || allowedEmails.existsById(Emails.normalize(email));
  }
}
