package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Shared check that a signed-in user survives being written to, and read back from, the session
 * table. That is what keeps people signed in across a restart or a redeploy (PLAN.md D7).
 */
final class SessionRoundTrip {

  private SessionRoundTrip() {}

  // The repository's session type is not public, so go through the generic SessionRepository API.
  @SuppressWarnings("unchecked")
  static void verify(JdbcIndexedSessionRepository jdbcSessions) {
    SessionRepository<Session> sessions =
        (SessionRepository<Session>) (SessionRepository<?>) jdbcSessions;
    var token =
        new OidcIdToken(
            "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "sub-rt"));
    var user = new SignedInUser(7L, "en", true, token, null);
    var context =
        new SecurityContextImpl(
            new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));

    Session session = sessions.createSession();
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    sessions.save(session);

    Session loaded = sessions.findById(session.getId());
    assertThat(loaded).isNotNull();
    SecurityContextImpl restored = loaded.getAttribute("SPRING_SECURITY_CONTEXT");
    var principal = (SignedInUser) restored.getAuthentication().getPrincipal();
    assertThat(principal.userId()).isEqualTo(7L);
    assertThat(principal.locale()).isEqualTo("en");
    assertThat(principal.isAdmin()).isTrue();
    assertThat(principal.getSubject()).isEqualTo("sub-rt");

    sessions.deleteById(session.getId());
    assertThat(sessions.findById(session.getId())).isNull();
  }
}
