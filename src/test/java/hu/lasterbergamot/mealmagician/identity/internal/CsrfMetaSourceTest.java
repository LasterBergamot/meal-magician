package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.shared.web.CsrfMeta;
import hu.lasterbergamot.mealmagician.shared.web.CsrfMetaSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class CsrfMetaSourceTest {

  private final CsrfMetaSource source = new SecurityConfig().csrfMetaSource();

  @Test
  void readsTheTokenSpringSecurityPutOnTheRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        CsrfToken.class.getName(), new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "abc"));

    assertThat(source.from(request)).isEqualTo(new CsrfMeta("X-CSRF-TOKEN", "abc"));
  }

  @Test
  void returnsNullForARequestWithoutToken() {
    assertThat(source.from(new MockHttpServletRequest())).isNull();
  }
}
