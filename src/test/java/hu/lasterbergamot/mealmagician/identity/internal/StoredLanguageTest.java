package hu.lasterbergamot.mealmagician.identity.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

class StoredLanguageTest {

  private final AppUserService users = mock(AppUserService.class);
  private final StoredLanguage store = new StoredLanguage(users);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void aSignedInSwitchIsSavedOnThePersonsRow() {
    var token =
        new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "s"));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                new SignedInUser(42L, "hu", false, token, null), null, "ROLE_USER"));

    store.remember(new MockHttpServletRequest(), Locale.of("en"));

    verify(users).chooseLocale(42L, "en");
  }

  @Test
  void anAnonymousSwitchSavesNothing() {
    store.remember(new MockHttpServletRequest(), Locale.of("en"));

    verifyNoInteractions(users);
  }

  @Test
  void aNonPersonPrincipalSavesNothing() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_ANONYMOUS"))));

    store.remember(new MockHttpServletRequest(), Locale.of("en"));

    verifyNoInteractions(users);
  }
}
