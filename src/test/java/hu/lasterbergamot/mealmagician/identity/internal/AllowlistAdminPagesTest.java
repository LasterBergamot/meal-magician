package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * The allowlist admin page through the whole stack (#45): who may open it, and that adding or
 * removing an address really changes who {@link AllowlistService} lets in. {@code boss@example.com}
 * is the bootstrap admin of this test.
 */
@SpringBootTest(properties = "mm.admin-emails=boss@example.com")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AllowlistAdminPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private AllowlistService allowlist;
  @Autowired private JdbcTemplate jdbc;

  private long adminId;

  /**
   * The admin who adds entries needs a real {@code app_user} row: {@code added_by} points at it.
   */
  @BeforeEach
  void insertAdmin() {
    jdbc.update(
        "insert into app_user (google_sub, email, display_name, locale, created_at)"
            + " values ('sub-boss', 'boss@example.com', 'Boss', 'hu', ?)",
        OffsetDateTime.now());
    adminId =
        jdbc.queryForObject("select id from app_user where google_sub = 'sub-boss'", Long.class);
  }

  private static RequestPostProcessor signedIn(long id, boolean admin) {
    var token =
        new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "sub-boss", "email", "boss@example.com"));
    var user = new SignedInUser(id, "hu", admin, token, null);
    return authentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
  }

  private ResultActions send(String path, String email, boolean htmx) throws Exception {
    MockHttpServletRequestBuilder request =
        post(path).param("email", email).with(signedIn(adminId, true)).with(csrf());
    return mvc.perform(htmx ? request.header("HX-Request", "true") : request);
  }

  @Test
  void aSignedInPersonWhoIsNotAnAdminGets403OnEveryAdminEndpoint() throws Exception {
    mvc.perform(get("/admin/allowlist").with(signedIn(adminId, false)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/allowlist")
                .param("email", "x@example.com")
                .with(signedIn(adminId, false))
                .with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/allowlist/remove")
                .param("email", "x@example.com")
                .with(signedIn(adminId, false))
                .with(csrf()))
        .andExpect(status().isForbidden());
    assertThat(allowlist.isAllowed("x@example.com")).isFalse();
  }

  @Test
  void anAnonymousVisitorIsSentToTheLoginPage() throws Exception {
    mvc.perform(get("/admin/allowlist"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void anAdminSeesTheBootstrapAdminWithoutARemoveButton() throws Exception {
    mvc.perform(get("/admin/allowlist").with(signedIn(adminId, true)))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "HX-Request"))
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("boss@example.com")))
        .andExpect(content().string(not(containsString("/admin/allowlist/remove"))));
  }

  @Test
  void anAdminSeesTheHeaderLinkAndAMemberDoesNot() throws Exception {
    mvc.perform(get("/admin/allowlist").with(signedIn(adminId, true)))
        .andExpect(content().string(containsString("href=\"/admin/allowlist\"")));
    mvc.perform(get("/").with(signedIn(adminId, false)))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("href=\"/admin/allowlist\""))));
  }

  @Test
  void addingAnAddressLetsThatPersonSignIn() throws Exception {
    assertThat(allowlist.isAllowed("guest@example.com")).isFalse();

    send("/admin/allowlist", "  Guest@Example.com ", false)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("guest@example.com")))
        .andExpect(content().string(containsString("/admin/allowlist/remove")));

    assertThat(allowlist.isAllowed("guest@example.com")).isTrue();
  }

  @Test
  void anHtmxAddAnswersWithTheSectionOnly() throws Exception {
    send("/admin/allowlist", "guest@example.com", true)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"allowlist\"")))
        .andExpect(content().string(not(containsString("<html"))));
  }

  @Test
  void removingAnAddressBlocksTheNextSignIn() throws Exception {
    send("/admin/allowlist", "guest@example.com", true);
    assertThat(allowlist.isAllowed("guest@example.com")).isTrue();

    send("/admin/allowlist/remove", "Guest@example.com", true)
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("guest@example.com"))));

    assertThat(allowlist.isAllowed("guest@example.com")).isFalse();
  }

  @Test
  void aBadAddressIsRejectedWith422AndKeptInTheField() throws Exception {
    send("/admin/allowlist", "not-an-email", true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Adj meg egy érvényes e-mail-címet.")))
        .andExpect(content().string(containsString("value=\"not-an-email\"")));
  }

  @Test
  void anAddressThatCanAlreadySignInIsRejectedWith422() throws Exception {
    send("/admin/allowlist", "BOSS@example.com", true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Ez a cím már be tud lépni.")));
  }

  @Test
  void aBootstrapAdminCannotBeRemovedAndStaysAllowed() throws Exception {
    send("/admin/allowlist/remove", "boss@example.com", true)
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("a szerver beállításában")));

    assertThat(allowlist.isAllowed("boss@example.com")).isTrue();
  }

  @Test
  void removingAnAddressThatIsNotListedChangesNothing() throws Exception {
    send("/admin/allowlist/remove", "nobody@example.com", false).andExpect(status().isOk());
  }
}
