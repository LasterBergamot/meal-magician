package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;

/**
 * Turns an invite code into the value stored in {@code invite.code_hash}: its SHA-256 as lower-case
 * hex. Storing only the hash means a leaked database row cannot be turned back into a working link.
 * A plain hash (no salt, no slow function) is right here: the code is 256 random bits, so there is
 * nothing to guess or look up in a table of common values, and the join page must find the row by
 * the hash alone. The work is done by {@link TokenCodes#hash}, which share links use too, so both
 * kinds of token are hashed the same way.
 */
public final class CodeHasher {

  private CodeHasher() {}

  /**
   * Hashes a code.
   *
   * @param code the raw code from a link; {@code null} is treated as empty so that a missing code
   *     simply matches no invite
   * @return 64 lower-case hex characters
   */
  public static String hash(String code) {
    return TokenCodes.hash(code);
  }
}
