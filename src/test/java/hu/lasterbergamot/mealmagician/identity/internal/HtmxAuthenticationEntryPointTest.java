package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class HtmxAuthenticationEntryPointTest {

  @Test
  void answers401WithAHxRedirectToTheLoginPage() {
    var request = new MockHttpServletRequest();
    request.setContextPath("/app");
    var response = new MockHttpServletResponse();

    new HtmxAuthenticationEntryPoint("/login")
        .commence(request, response, new BadCredentialsException("expired"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("HX-Redirect")).isEqualTo("/app/login");
  }
}
