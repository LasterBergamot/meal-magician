package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the CSRF token of the current request to the layout.
 *
 * <p>There is no implementation until Spring Security arrives (issue #12), and the layout then
 * renders no CSRF {@code <meta>} tags. The security configuration publishes one bean of this type
 * that reads the {@code CsrfToken} request attribute; this indirection keeps the {@code shared}
 * module free of a Spring Security dependency and of reflection.
 */
@FunctionalInterface
public interface CsrfMetaSource {

  /**
   * Reads the token for a request.
   *
   * @param request the current request
   * @return the token, or {@code null} if this request has none
   */
  CsrfMeta from(HttpServletRequest request);
}
