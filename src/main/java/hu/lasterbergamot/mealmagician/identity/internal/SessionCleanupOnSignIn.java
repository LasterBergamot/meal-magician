package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

/**
 * After a successful sign-in, deletes expired sessions from the database, applies the language the
 * person chose the last time (stored per user, see {@link SignInLanguage}), then continues to the
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
  private final SignInLanguage language;

  /**
   * Creates the handler.
   *
   * @param sessions the session table, for the clean-up
   * @param language applies the person's stored language to this browser
   */
  SessionCleanupOnSignIn(JdbcIndexedSessionRepository sessions, SignInLanguage language) {
    this.sessions = sessions;
    this.language = language;
  }

  /**
   * Cleans up expired sessions, applies the stored language, then redirects as the default handler
   * would.
   *
   * <p>The principal is always a {@link SignedInUser}: the OIDC user service that runs before this
   * handler returns one or refuses the sign-in, so the cast cannot fail here.
   *
   * @param request the callback request from Google
   * @param response the response to redirect and to set the language cookie on
   * @param authentication the person who just signed in
   * @throws IOException if the redirect cannot be written
   * @throws ServletException if the redirect fails
   */
  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    sessions.cleanUpExpiredSessions();
    language.apply(request, response, (SignedInUser) authentication.getPrincipal());
    super.onAuthenticationSuccess(request, response, authentication);
  }
}
