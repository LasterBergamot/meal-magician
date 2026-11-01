package hu.lasterbergamot.mealmagician.identity.internal;

import java.time.Instant;

/**
 * One address that may sign in, as the admin page lists it.
 *
 * @param email the normalized address
 * @param origin where it comes from
 * @param addedAt when it was added, or {@code null} for a bootstrap admin, which has no row
 */
record AllowlistEntry(String email, AllowlistOrigin origin, Instant addedAt) {

  /**
   * Whether the page may offer to remove this entry.
   *
   * @return {@code false} for bootstrap admins
   */
  boolean removable() {
    return origin != AllowlistOrigin.BOOTSTRAP;
  }
}
