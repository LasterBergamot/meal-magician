package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class SignInFailureHandlerTest {

  private final SignInFailureHandler handler = new SignInFailureHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void refusedAccountGets403() throws Exception {
    handler.onAuthenticationFailure(
        request,
        response,
        new OAuth2AuthenticationException(new OAuth2Error(AllowlistedOidcUserService.NOT_ALLOWED)));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void cancelledConsentGoesBackToTheLoginPage() throws Exception {
    handler.onAuthenticationFailure(
        request, response, new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
  }

  @Test
  void otherFailuresGoBackToTheLoginPage() throws Exception {
    handler.onAuthenticationFailure(request, response, new BadCredentialsException("x"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
  }
}
