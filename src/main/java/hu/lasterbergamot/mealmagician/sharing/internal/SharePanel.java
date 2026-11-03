package hu.lasterbergamot.mealmagician.sharing.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.util.UriUtils;

/**
 * Everything the share sheet renders for one week: the heading, the links already made and, in the
 * one response that creates a link, the link itself.
 *
 * @param week the week code ({@code 2026-W45}), posted back with every action
 * @param title the translated week heading, for example {@code Week 45}
 * @param range the translated first and last date of the week
 * @param rows the week's links, newest first
 * @param expiry the selected expiry code ({@code 1d}, {@code 7d} or {@code 30d})
 * @param error message key of a refused create (the cap of active links), or {@code null}
 * @param link the full {@code /share/<token>} address of a link created by this very response, or
 *     {@code null}; the token is not stored anywhere, so it cannot be shown again
 */
public record SharePanel(
    String week,
    String title,
    String range,
    List<ShareRow> rows,
    String expiry,
    String error,
    String link) {

  /**
   * The same panel showing a freshly created link. It is the only place a link is ever set.
   *
   * @param newLink the full share address
   * @return the changed panel
   */
  SharePanel created(String newLink) {
    return new SharePanel(week, title, range, rows, expiry, null, newLink);
  }

  /**
   * The same panel with the refusal of a create and what the person picked.
   *
   * @param errorKey message key of the refusal
   * @param pickedExpiry the expiry they had chosen, kept in the form
   * @return the changed panel
   */
  SharePanel rejected(String errorKey, String pickedExpiry) {
    return new SharePanel(week, title, range, rows, pickedExpiry, errorKey, null);
  }

  /**
   * The share address of a token.
   *
   * @param base the application's base address, for example {@code https://meal.example}; built
   *     from the request so a proxy's forwarded scheme and host apply
   * @param token the raw token
   * @return {@code base} + {@code /share/} + the token, percent-encoded as one path segment
   */
  static String linkFor(String base, String token) {
    return base + "/share/" + UriUtils.encodePathSegment(token, StandardCharsets.UTF_8);
  }
}
