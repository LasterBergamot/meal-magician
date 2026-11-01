package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.Locale;

/**
 * E-mail address helpers. Public only so {@link
 * hu.lasterbergamot.mealmagician.identity.AllowlistEditor} (the module's public API) can use it;
 * other modules cannot see the {@code internal} package.
 */
public final class Emails {

  private Emails() {}

  /**
   * Brings an address into the form we store and compare: trimmed and lower-case.
   *
   * <p>Google treats the local part of an address as case-insensitive, and so must the allowlist;
   * otherwise {@code Alice@Gmail.com} on the list would not match {@code alice@gmail.com} from
   * Google. {@link Locale#ROOT} avoids the Turkish dotless-i surprise.
   *
   * @param email the raw address, may be {@code null}
   * @return the normalized address, or an empty string for {@code null}
   */
  public static String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
