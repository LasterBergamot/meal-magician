package hu.lasterbergamot.mealmagician.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Handles an anonymous htmx request, typically a click after the session expired.
 *
 * <p>A normal 302 to the login page would be followed by the browser's XHR, and htmx would then
 * swap the whole login page into a small fragment of the screen. Instead we answer 401 with {@code
 * HX-Redirect}, which tells htmx to navigate the whole window to the login page.
 */
class HtmxAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final String loginUrl;

  HtmxAuthenticationEntryPoint(String loginUrl) {
    this.loginUrl = loginUrl;
  }

  /**
   * Answers 401 plus {@code HX-Redirect} to the login page.
   *
   * @param request the anonymous htmx request
   * @param response the response to fill in
   * @param authException why authentication is required
   */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader("HX-Redirect", request.getContextPath() + loginUrl);
  }
}
