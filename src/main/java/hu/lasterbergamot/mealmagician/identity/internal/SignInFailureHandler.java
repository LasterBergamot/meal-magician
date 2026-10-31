package hu.lasterbergamot.mealmagician.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Decides what a failed Google sign-in looks like.
 *
 * <p>A refused account (not on the allowlist, or unverified e-mail) gets a real <b>403</b>, as the
 * plan requires (D6); Spring's error page renders it. Every other failure, such as the person
 * pressing "Cancel" on Google's consent screen, goes back to the login page with {@code ?error} so
 * they can try again.
 */
@Component
class SignInFailureHandler implements AuthenticationFailureHandler {

  /** Where the person goes after a failure that is not a refusal. */
  static final String LOGIN_ERROR_URL = "/login?error";

  /**
   * Responds to a failed sign-in.
   *
   * @param request the callback request from Google
   * @param response the response to fill in
   * @param exception why the sign-in failed
   * @throws IOException if the response cannot be written
   */
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    if (exception instanceof OAuth2AuthenticationException oauth
        && AllowlistedOidcUserService.NOT_ALLOWED.equals(oauth.getError().getErrorCode())) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    response.sendRedirect(request.getContextPath() + LOGIN_ERROR_URL);
  }
}
