package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

class SessionCleanupOnSignInTest {

  private static SignedInUser person() {
    var token =
        new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "sub-7", "email", "anna@example.com"));
    return new SignedInUser(7L, "en", false, token, null);
  }

  @Test
  void deletesExpiredSessionsWhenSomeoneSignsIn() throws Exception {
    var sessions = mock(JdbcIndexedSessionRepository.class);
    var language = mock(SignInLanguage.class);
    var user = person();

    new SessionCleanupOnSignIn(sessions, language)
        .onAuthenticationSuccess(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new TestingAuthenticationToken(user, null, "ROLE_USER"));

    verify(sessions).cleanUpExpiredSessions();
  }

  @Test
  void appliesThePersonsStoredLanguageWhenTheySignIn() throws Exception {
    var sessions = mock(JdbcIndexedSessionRepository.class);
    var language = mock(SignInLanguage.class);
    var user = person();
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    new SessionCleanupOnSignIn(sessions, language)
        .onAuthenticationSuccess(
            request, response, new TestingAuthenticationToken(user, null, "ROLE_USER"));

    verify(language).apply(request, response, user);
    assertThat(response.getRedirectedUrl()).isEqualTo("/");
  }
}
