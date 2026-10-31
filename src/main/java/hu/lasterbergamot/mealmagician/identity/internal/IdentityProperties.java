package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the {@code mm} prefix.
 *
 * <p>{@code adminEmails} comes from the {@code MM_ADMIN_EMAILS} variable (comma-separated), mapped
 * in {@code application.yml}. They are the bootstrap instance admins: always allowed to sign in,
 * and the only people who manage the allowlist.
 *
 * @param adminEmails the raw admin addresses; {@code null} is treated as none
 */
@ConfigurationProperties(prefix = "mm")
public record IdentityProperties(List<String> adminEmails) {

  /**
   * The admin addresses in normalized form, ready to compare with a Google e-mail.
   *
   * @return lower-case admin addresses without blanks
   */
  public Set<String> normalizedAdminEmails() {
    if (adminEmails == null) {
      return Set.of();
    }
    return adminEmails.stream()
        .map(Emails::normalize)
        .filter(email -> !email.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
