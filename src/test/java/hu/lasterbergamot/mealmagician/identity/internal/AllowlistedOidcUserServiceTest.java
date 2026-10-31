package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AllowlistedOidcUserServiceTest {

  private final OAuth2UserService<OidcUserRequest, OidcUser> google = mockGoogle();
  private final AllowlistService allowlist = mock(AllowlistService.class);
  private final AppUserService users = mock(AppUserService.class);
  private final AllowlistedOidcUserService service =
      new AllowlistedOidcUserService(google, allowlist, users);
  private final OidcUserRequest request = mock(OidcUserRequest.class);

  @SuppressWarnings("unchecked")
  private static OAuth2UserService<OidcUserRequest, OidcUser> mockGoogle() {
    return mock(OAuth2UserService.class);
  }

  private static OidcUser googleUser(Map<String, Object> extraClaims) {
    var claims = new java.util.HashMap<String, Object>(Map.of("sub", "sub-1"));
    claims.putAll(extraClaims);
    var token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60), claims);
    return new DefaultOidcUser(java.util.List.of(), token);
  }

  private static AppUser savedUser(String email, String name) {
    var user = new AppUser("sub-1", email, name, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
    return user;
  }

  private void googleReturns(Map<String, Object> claims) {
    when(google.loadUser(any())).thenReturn(googleUser(claims));
  }

  private void assertRefused() {
    assertThatThrownBy(() -> service.loadUser(request))
        .isInstanceOfSatisfying(
            OAuth2AuthenticationException.class,
            e ->
                assertThat(e.getError().getErrorCode())
                    .isEqualTo(AllowlistedOidcUserService.NOT_ALLOWED));
    verify(users, never()).recordSignIn(any(), any(), any());
  }

  @Test
  void refusesAnUnverifiedEmailEvenIfAllowlisted() {
    googleReturns(Map.of("email", "a@x.hu", "email_verified", false));
    when(allowlist.isAllowed("a@x.hu")).thenReturn(true);

    assertRefused();
  }

  @Test
  void refusesAMissingEmailVerifiedClaim() {
    googleReturns(Map.of("email", "a@x.hu"));
    when(allowlist.isAllowed("a@x.hu")).thenReturn(true);

    assertRefused();
  }

  @Test
  void refusesAMissingEmail() {
    googleReturns(Map.of("email_verified", true));

    assertRefused();
  }

  @Test
  void refusesAnEmailThatIsNotOnTheAllowlistAndCreatesNoUser() {
    googleReturns(Map.of("email", "stranger@x.hu", "email_verified", true));
    when(allowlist.isAllowed("stranger@x.hu")).thenReturn(false);

    assertRefused();
  }

  @Test
  void signsInAnAllowlistedUserWithNormalizedEmailAndName() {
    googleReturns(Map.of("email", "Alice@X.hu", "email_verified", true, "name", "Alice A"));
    when(allowlist.isAllowed("alice@x.hu")).thenReturn(true);
    when(users.recordSignIn("sub-1", "alice@x.hu", "Alice A"))
        .thenReturn(savedUser("alice@x.hu", "Alice A"));

    var principal = (SignedInUser) service.loadUser(request);

    assertThat(principal.userId()).isEqualTo(42L);
    assertThat(principal.locale()).isEqualTo("hu");
    assertThat(principal.isAdmin()).isFalse();
    assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  void usesTheEmailAsNameWhenGoogleSendsNone() {
    googleReturns(Map.of("email", "a@x.hu", "email_verified", true));
    when(allowlist.isAllowed("a@x.hu")).thenReturn(true);
    when(users.recordSignIn("sub-1", "a@x.hu", "a@x.hu")).thenReturn(savedUser("a@x.hu", "a@x.hu"));

    service.loadUser(request);

    verify(users).recordSignIn("sub-1", "a@x.hu", "a@x.hu");
  }

  @Test
  void adminsGetTheAdminAuthority() {
    googleReturns(Map.of("email", "boss@x.hu", "email_verified", true, "name", "Boss"));
    when(allowlist.isAllowed("boss@x.hu")).thenReturn(true);
    when(allowlist.isAdmin("boss@x.hu")).thenReturn(true);
    when(users.recordSignIn("sub-1", "boss@x.hu", "Boss"))
        .thenReturn(savedUser("boss@x.hu", "Boss"));

    var principal = (SignedInUser) service.loadUser(request);

    assertThat(principal.isAdmin()).isTrue();
    assertThat(principal.getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
  }
}
