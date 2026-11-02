package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.Locale;

/**
 * One line of the admin page's list, ready to render.
 *
 * @param email the address
 * @param originKey message key naming where the entry comes from
 * @param added when it was added, already formatted, or an empty string for a bootstrap admin
 * @param removable whether a remove button makes sense
 */
public record AllowlistRow(String email, String originKey, String added, boolean removable) {

  /**
   * The tone of the badge that names where this entry comes from ({@code ui/_badge.jte}): brand for
   * a bootstrap admin, info for a household invite, neutral for an address added on the page. It
   * lives here and not in the template because the template guard reads inline code in a template
   * as visible text.
   *
   * @return a badge tone: {@code brand}, {@code info} or {@code neutral}
   */
  public String badgeTone() {
    if (originKey.equals(AllowlistOrigin.BOOTSTRAP.messageKey())) {
      return "brand";
    }
    if (originKey.equals(AllowlistOrigin.INVITE.messageKey())) {
      return "info";
    }
    return "neutral";
  }

  /**
   * A string that is safe in an HTML id and unique per address on the page, used to tie a row's
   * hidden address input to its confirm dialog. An address may hold characters that are not valid
   * in an id, so the id is a hash of it, not the address itself. Two addresses could share a hash
   * in theory; the page lists a handful of addresses, so that is accepted.
   *
   * @return {@code allowlist-} followed by the hexadecimal hash of the lower-cased address
   */
  public String domId() {
    return "allowlist-" + Integer.toHexString(email.toLowerCase(Locale.ROOT).hashCode());
  }
}
