package hu.lasterbergamot.mealmagician.household;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Helpers shared by the household tests. People are inserted with plain SQL because the {@code
 * identity} module's repositories are internal to it; the tests only need a row to point a
 * membership at.
 */
public final class HouseholdTestSupport {

  private HouseholdTestSupport() {}

  /**
   * Inserts an {@code app_user} row.
   *
   * @param jdbc the template of the test database
   * @param name used to build a unique Google sub and e-mail
   * @return the new user's id
   */
  public static long insertUser(JdbcTemplate jdbc, String name) {
    jdbc.update(
        "insert into app_user (google_sub, email, display_name, locale, created_at)"
            + " values (?, ?, ?, 'hu', ?)",
        "sub-" + name,
        name + "@example.com",
        name,
        OffsetDateTime.now());
    return jdbc.queryForObject(
        "select id from app_user where google_sub = ?", Long.class, "sub-" + name);
  }

  /**
   * A signed-in person as the security layer would build them.
   *
   * @param userId the {@code app_user} id
   * @return the principal
   */
  public static SignedInUser signedIn(long userId) {
    var token =
        new OidcIdToken(
            "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "sub-" + userId));
    return new SignedInUser(userId, "hu", false, token, null);
  }

  /**
   * Makes a MockMvc request come from a signed-in person.
   *
   * @param userId the {@code app_user} id
   * @return the request post-processor
   */
  public static RequestPostProcessor signedInAs(long userId) {
    SignedInUser user = signedIn(userId);
    return authentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
  }
}
