package hu.lasterbergamot.mealmagician.identity;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * The signed-in person, as every controller and service sees them.
 *
 * <p>It is the Spring Security principal: a Google {@code OidcUser} that also carries our own
 * {@code app_user} id, which is what other modules store and query by. Controllers get it with
 * {@code @AuthenticationPrincipal SignedInUser user}.
 *
 * <p>The principal is stored in the HTTP session, which lives in the database (PLAN.md D7), so the
 * class is {@link java.io.Serializable} (inherited) and must only hold serializable fields. The
 * admin flag is decided at sign-in: changing {@code MM_ADMIN_EMAILS} takes effect at the person's
 * next sign-in, not mid-session.
 */
public class SignedInUser extends DefaultOidcUser {

  /** Authority every signed-in user has. */
  public static final String ROLE_USER = "ROLE_USER";

  /** Extra authority for instance admins, the people named in {@code MM_ADMIN_EMAILS}. */
  public static final String ROLE_ADMIN = "ROLE_ADMIN";

  @Serial private static final long serialVersionUID = 1L;

  private final long userId;
  private final String locale;
  private final boolean admin;

  /**
   * Creates the principal.
   *
   * @param userId the {@code app_user} id
   * @param locale the person's UI language, {@code hu} or {@code en}
   * @param admin whether the person is an instance admin
   * @param idToken the validated Google ID token
   * @param userInfo Google's user-info claims, may be {@code null}
   */
  public SignedInUser(
      long userId, String locale, boolean admin, OidcIdToken idToken, OidcUserInfo userInfo) {
    super(authorities(admin), idToken, userInfo);
    this.userId = userId;
    this.locale = locale;
    this.admin = admin;
  }

  private static Collection<GrantedAuthority> authorities(boolean admin) {
    var authorities = new ArrayList<GrantedAuthority>();
    authorities.add(new SimpleGrantedAuthority(ROLE_USER));
    if (admin) {
      authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
    }
    return authorities;
  }

  /**
   * The id of the {@code app_user} row; use this, not the e-mail, to refer to a person.
   *
   * @return the user id
   */
  public long userId() {
    return userId;
  }

  /**
   * The person's UI language as stored when they signed in.
   *
   * @return {@code hu} or {@code en}
   */
  public String locale() {
    return locale;
  }

  /**
   * Whether the person may manage the allowlist.
   *
   * @return {@code true} for instance admins
   */
  public boolean isAdmin() {
    return admin;
  }
}
