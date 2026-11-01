package hu.lasterbergamot.mealmagician.identity.internal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

/**
 * After a successful sign-in, deletes expired sessions from the database, then continues to the
 * page the person originally asked for.
 *
 * <p>Spring Session normally does this clean-up on a timer. A timer would query the database every
 * minute and keep the database from scaling to zero (PLAN.md D11), so it is switched off in {@code
 * application.yml}. Expired sessions are never served (they are ignored when read), so the only
 * cost of waiting is a few stale rows; sign-ins are rare and a natural moment to clear them.
 */
@Component
class SessionCleanupOnSignIn extends SavedRequestAwareAuthenticationSuccessHandler {

  private final JdbcIndexedSessionRepository sessions;

  SessionCleanupOnSignIn(JdbcIndexedSessionRepository sessions) {
    this.sessions = sessions;
  }

  /**
   * Cleans up expired sessions, then redirects as the default handler would.
   *
   * @param request the callback request from Google
   * @param response the response to redirect
   * @param authentication the person who just signed in
   * @throws IOException if the redirect cannot be written
   * @throws ServletException if the redirect fails
   */
  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    sessions.cleanUpExpiredSessions();
    super.onAuthenticationSuccess(request, response, authentication);
  }
}
