package hu.lasterbergamot.mealmagician.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The one place that makes, shapes and hashes bearer tokens: invite codes and share-link tokens
 * (PLAN.md D9 and D28). A bearer token is a secret in a URL, so whoever holds it may use it.
 *
 * <p>The rules, kept together so they cannot drift apart: a token is 256 random bits from {@link
 * SecureRandom}, written as 43 base64url characters; only its SHA-256 hash is stored, and the raw
 * token is shown once and never logged. A plain hash (no salt, no slow function) is right because
 * there is nothing to guess: the token is already as random as a key, and the lookup must find the
 * row by the hash alone.
 */
public final class TokenCodes {

  private static final int TOKEN_BYTES = 32;

  /** 32 bytes in base64url without padding are exactly 43 characters. */
  private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{43}");

  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenCodes() {}

  /**
   * Makes a new token.
   *
   * @return 43 URL-safe characters ({@code A-Z a-z 0-9 - _}) holding 256 random bits; it is the raw
   *     secret, so hand it to the person once and store only {@link #hash(String)} of it
   */
  public static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Hashes a token into the value stored in the database.
   *
   * @param token the raw token from a link; {@code null} is treated as empty so that a missing
   *     token simply matches no row
   * @return 64 lower-case hex characters (SHA-256)
   */
  public static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // Every Java runtime must provide SHA-256, so this cannot happen.
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /**
   * Checks the shape of a token before it is used for anything. A public route calls this first, so
   * a request with an obviously wrong token is refused without touching the database.
   *
   * @param token the text from a URL, may be {@code null}
   * @return {@code true} only for exactly 43 base64url characters, which is what {@link
   *     #generate()} makes
   */
  public static boolean isWellFormed(String token) {
    return token != null && WELL_FORMED.matcher(token).matches();
  }
}
