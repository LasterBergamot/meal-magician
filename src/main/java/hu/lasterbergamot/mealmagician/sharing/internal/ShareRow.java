package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.sharing.ShareLinkStatus;
import hu.lasterbergamot.mealmagician.sharing.ShareLinkView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * One line of the share sheet's list, ready to render. It holds no token and no hash: the service
 * never returns them for a list.
 *
 * @param id the link id, for the Revoke button
 * @param statusKey message key of the status word ({@code share.status.active}, ...)
 * @param tone the badge tone for the status: {@code success}, {@code neutral} or {@code danger}
 * @param expires when the link stops working, formatted in the application's time zone
 * @param createdBy the display name of the member who made it, or {@code null} when that person is
 *     no longer a member of the household
 * @param revocable whether a Revoke button makes sense, which is only while the link is active
 */
public record ShareRow(
    long id, String statusKey, String tone, String expires, String createdBy, boolean revocable) {

  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  /**
   * Turns a service view into a row.
   *
   * @param view the link as the service lists it
   * @param names display names of the household's members by user id
   * @param zone the time zone the expiry is shown in
   * @return the row; {@code createdBy} is {@code null} when the creator has no entry in {@code
   *     names} (they left, or their account was deleted)
   */
  static ShareRow of(ShareLinkView view, Map<Long, String> names, ZoneId zone) {
    ShareLinkStatus status = view.status();
    String tone =
        switch (status) {
          case ACTIVE -> "success";
          case EXPIRED -> "neutral";
          case REVOKED -> "danger";
        };
    return new ShareRow(
        view.id(),
        "share.status." + status.name().toLowerCase(Locale.ROOT),
        tone,
        WHEN.format(view.expiresAt().atZone(zone)),
        view.createdBy() == null ? null : names.get(view.createdBy()),
        status == ShareLinkStatus.ACTIVE);
  }
}
