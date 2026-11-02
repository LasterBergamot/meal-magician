package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.ShellUser;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

class ShellUserBeanTest {

  private final ShellUserBean bean = new ShellUserBean();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static SignedInUser person(Map<String, Object> claims) {
    var token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
    return new SignedInUser(3L, "hu", false, token, null);
  }

  @Test
  void anAnonymousRequestHasNoPerson() {
    assertThat(bean.from(new MockHttpServletRequest())).isNull();
  }

  @Test
  void aPrincipalThatIsNotASignedInUserHasNoPerson() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("someone", "secret"));

    assertThat(bean.from(new MockHttpServletRequest())).isNull();
  }

  @Test
  void theShellShowsGoogleFullNameWhenThereIsOne() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                person(Map.of("sub", "s", "name", "Török András")), null, "ROLE_USER"));

    assertThat(bean.from(new MockHttpServletRequest())).isEqualTo(new ShellUser("Török András"));
  }

  @Test
  void theShellFallsBackToTheEmailWhenGoogleGaveNoName() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                person(Map.of("sub", "s", "email", "anna@example.com")), null, "ROLE_USER"));

    assertThat(bean.from(new MockHttpServletRequest()))
        .isEqualTo(new ShellUser("anna@example.com"));
  }
}
