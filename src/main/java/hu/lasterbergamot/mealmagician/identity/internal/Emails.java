package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * E-mail address helpers. Public only so {@link
 * hu.lasterbergamot.mealmagician.identity.AllowlistEditor} (the module's public API) can use it;
 * other modules cannot see the {@code internal} package.
 */
public final class Emails {

  /** The longest address SMTP allows; also what the {@code allowed_email} column can hold. */
  public static final int MAX_LENGTH = 254;

  private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

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

  /**
   * Tells whether text is shaped like an e-mail address: one {@code @}, no spaces, a dot in the
   * domain, at most {@link #MAX_LENGTH} characters. It is a typo check, not proof the mailbox
   * exists; Google's verified address is what actually gets compared at sign-in.
   *
   * @param normalized an address already passed through {@link #normalize(String)}
   * @return {@code true} if it looks like an address
   */
  public static boolean isValid(String normalized) {
    return normalized.length() <= MAX_LENGTH && SHAPE.matcher(normalized).matches();
  }
}
