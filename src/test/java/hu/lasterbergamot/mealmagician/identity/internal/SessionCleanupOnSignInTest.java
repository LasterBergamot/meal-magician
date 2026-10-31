package hu.lasterbergamot.mealmagician.identity.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

class SessionCleanupOnSignInTest {

  @Test
  void deletesExpiredSessionsWhenSomeoneSignsIn() throws Exception {
    var sessions = mock(JdbcIndexedSessionRepository.class);

    new SessionCleanupOnSignIn(sessions)
        .onAuthenticationSuccess(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new TestingAuthenticationToken("u", "p"));

    verify(sessions).cleanUpExpiredSessions();
  }
}
