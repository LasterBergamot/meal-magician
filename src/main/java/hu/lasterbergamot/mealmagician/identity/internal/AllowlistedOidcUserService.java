package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * The gate between "Google says this is Alice" and "Alice may use the app".
 *
 * <p>Spring Security has already validated the ID token when this runs. We then require a
 * <em>verified</em> e-mail (an unverified address could be claimed by anyone) and a place on the
 * allowlist. Anyone else is refused with {@link #NOT_ALLOWED}, which {@link SignInFailureHandler}
 * turns into a 403, and no {@code app_user} row is created for them.
 */
@Service
class AllowlistedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  /** OAuth2 error code for "signed in at Google, but not permitted here". */
  static final String NOT_ALLOWED = "mm_not_allowed";

  private final OAuth2UserService<OidcUserRequest, OidcUser> googleUserService;
  private final AllowlistService allowlist;
  private final AppUserService users;

  @Autowired
  AllowlistedOidcUserService(AllowlistService allowlist, AppUserService users) {
    this(new OidcUserService(), allowlist, users);
  }

  /** For tests: lets them replace the call to Google. */
  AllowlistedOidcUserService(
      OAuth2UserService<OidcUserRequest, OidcUser> googleUserService,
      AllowlistService allowlist,
      AppUserService users) {
    this.googleUserService = googleUserService;
    this.allowlist = allowlist;
    this.users = users;
  }

  /**
   * Loads the Google user and turns them into a {@link SignedInUser}.
   *
   * @param request the validated OIDC login request
   * @return the signed-in user, backed by an {@code app_user} row
   * @throws OAuth2AuthenticationException with {@link #NOT_ALLOWED} if the e-mail is missing,
   *     unverified or not on the allowlist
   */
  @Override
  public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
    var google = googleUserService.loadUser(request);
    var email = Emails.normalize(google.getEmail());
    if (!Boolean.TRUE.equals(google.getEmailVerified())
        || email.isEmpty()
        || !allowlist.isAllowed(email)) {
      // The address is deliberately not logged or put in the message (security.md).
      throw new OAuth2AuthenticationException(new OAuth2Error(NOT_ALLOWED));
    }
    var name = google.getFullName() == null ? email : google.getFullName();
    var user = users.recordSignIn(google.getSubject(), email, name);
    return new SignedInUser(
        user.getId(),
        user.getLocale(),
        allowlist.isAdmin(email),
        google.getIdToken(),
        google.getUserInfo());
  }
}
