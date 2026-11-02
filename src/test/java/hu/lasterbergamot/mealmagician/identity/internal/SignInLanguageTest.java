package hu.lasterbergamot.mealmagician.identity.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.servlet.LocaleResolver;

class SignInLanguageTest {

  private static SignedInUser personWithLocale(String locale) {
    var token =
        new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "sub-1", "email", "anna@example.com"));
    return new SignedInUser(1L, locale, false, token, null);
  }

  @Test
  void theStoredEnglishFollowsThePersonToTheNewBrowser() {
    var resolver = mock(LocaleResolver.class);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    new SignInLanguage(resolver).apply(request, response, personWithLocale("en"));

    verify(resolver).setLocale(request, response, Locale.of("en"));
  }

  @Test
  void aStoredValueThisVersionDoesNotSupportFallsBackToHungarian() {
    var resolver = mock(LocaleResolver.class);

    new SignInLanguage(resolver)
        .apply(new MockHttpServletRequest(), new MockHttpServletResponse(), personWithLocale("de"));

    verify(resolver).setLocale(any(), any(), eq(Locale.of("hu")));
  }
}
