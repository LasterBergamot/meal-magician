package hu.lasterbergamot.mealmagician.identity.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removing an address from the allowlist ends that person's live session (#59): their next request
 * goes to the sign-in page, while a session of someone who stays on the list keeps working. Runs
 * the real session store and the admin endpoint, so the principal name that the session is found by
 * is the one Spring Security really stores.
 *
 * <p>The sessions are written straight to the session store with the security context in them, as a
 * real sign-in leaves them. A MockMvc sign-in does not persist the context, so it would prove
 * nothing about the store.
 *
 * <p>{@code boss@example.com} is the bootstrap admin; {@code guest} is removed, {@code anna} stays.
 */
@SpringBootTest(properties = "mm.admin-emails=boss@example.com")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AllowlistRemovalEndsSessionsTest {

  @Autowired private MockMvc mvc;
  @Autowired private AllowlistAdminService allowlist;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JdbcIndexedSessionRepository sessionStore;

  private long adminId;
  private long guestId;
  private long annaId;

  /** Three people with {@code app_user} rows; the guest and Anna are on the allowlist. */
  @BeforeEach
  void insertPeople() {
    adminId = insertUser("boss");
    guestId = insertUser("guest");
    annaId = insertUser("anna");
    allowlist.add("guest@example.com", adminId);
    allowlist.add("anna@example.com", adminId);
  }

  private long insertUser(String name) {
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

  private static RequestPostProcessor signedIn(long id, String name, boolean admin) {
    var token =
        new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "sub-" + name, "email", name + "@example.com"));
    var user = new SignedInUser(id, "hu", admin, token, null);
    return authentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
  }

  /**
   * Stores a signed-in session the way a completed sign-in does, then returns the {@code SESSION}
   * cookie that points at it. The cookie value is the session id, base64-encoded, which is how
   * Spring Session writes it by default.
   */
  @SuppressWarnings("unchecked")
  private Cookie storedSessionOf(long id, String name) {
    SessionRepository<Session> store =
        (SessionRepository<Session>) (SessionRepository<?>) sessionStore;
    var token =
        new OidcIdToken(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "sub-" + name, "email", name + "@example.com"));
    var user = new SignedInUser(id, "hu", false, token, null);
    var context =
        new SecurityContextImpl(
            new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));

    Session session = store.createSession();
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    store.save(session);
    String cookieValue =
        Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
    return new Cookie("SESSION", cookieValue);
  }

  private void removeGuest() throws Exception {
    mvc.perform(
            post("/admin/allowlist/remove")
                .param("email", "guest@example.com")
                .with(signedIn(adminId, "boss", true))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void removingAnAddressSendsThatPersonToSignInOnTheirNextRequest() throws Exception {
    Cookie guestSession = storedSessionOf(guestId, "guest");
    mvc.perform(get("/households").cookie(guestSession)).andExpect(status().isOk());

    removeGuest();

    mvc.perform(get("/households").cookie(guestSession))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void removingAnAddressLeavesTheSessionsOfEveryoneElseWorking() throws Exception {
    Cookie guestSession = storedSessionOf(guestId, "guest");
    Cookie annaSession = storedSessionOf(annaId, "anna");
    mvc.perform(get("/households").cookie(annaSession)).andExpect(status().isOk());

    removeGuest();

    mvc.perform(get("/households").cookie(annaSession)).andExpect(status().isOk());
    mvc.perform(get("/households").cookie(guestSession))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login"));
  }
}
