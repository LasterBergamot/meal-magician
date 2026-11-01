package hu.lasterbergamot.mealmagician.household;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The households page and its forms through the whole stack (security, controller, database,
 * templates). Anna owns "Annas" and is a plain member of "Bela's"; Bela owns "Belas" and has no
 * access to "Annas": the 404 and 403 cases are the tenant-safety rules of PLAN.md D8.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class HouseholdPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService service;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private HouseholdService spiedService;

  private long anna;
  private long bela;
  private long annas;
  private long belas;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annas = service.create(anna, "Annas").id();
    belas = service.create(bela, "Belas").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', ?)",
        belas,
        anna,
        java.time.OffsetDateTime.ofInstant(Instant.now(), java.time.ZoneOffset.UTC));
  }

  /**
   * Starts a browser-like session: the first request makes the app create the session, and the
   * cookie it sets is what a later request sends back (Spring Session keeps sessions in the
   * database and finds them by that cookie, not by a {@code MockHttpSession}).
   */
  private Cookie signInSession(long userId) throws Exception {
    Cookie cookie =
        mvc.perform(get("/households").with(signedInAs(userId)))
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    assertThat(cookie).as("the app sets a session cookie").isNotNull();
    return cookie;
  }

  @Test
  void listsMyHouseholdsWithRolesAndTheCurrentOne() throws Exception {
    mvc.perform(get("/households").with(signedInAs(anna)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Háztartásaim")))
        .andExpect(content().string(containsString("Annas")))
        .andExpect(content().string(containsString("Belas")))
        .andExpect(content().string(containsString("Tulajdonos")))
        .andExpect(content().string(containsString("Tag")))
        .andExpect(content().string(containsString("aria-current=\"true\"")))
        .andExpect(content().string(containsString("hx-post=\"/households/" + belas + "/switch\"")))
        .andExpect(header().string("Vary", "HX-Request"));
  }

  @Test
  void theRenameFormIsOnlyForOwnersOfTheCurrentHousehold() throws Exception {
    Cookie session = signInSession(anna);
    mvc.perform(get("/households").with(signedInAs(anna)).cookie(session))
        .andExpect(content().string(containsString("id=\"rename-form\"")))
        .andExpect(content().string(containsString("/households/" + annas + "/rename")));

    mvc.perform(
        post("/households/" + belas + "/switch")
            .with(signedInAs(anna))
            .with(csrf())
            .cookie(session));
    mvc.perform(get("/households").with(signedInAs(anna)).cookie(session))
        .andExpect(content().string(not(containsString("id=\"rename-form\""))));
  }

  @Test
  void aPersonWithoutHouseholdsSeesTheEmptyStateAndTheCreateForm() throws Exception {
    long newcomer = HouseholdTestSupport.insertUser(jdbc, "newcomer");

    mvc.perform(get("/households").with(signedInAs(newcomer)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Még nem tartozol egy háztartáshoz sem")))
        .andExpect(content().string(containsString("id=\"create-form\"")))
        .andExpect(content().string(not(containsString("id=\"rename-form\""))));
  }

  @Test
  void htmxGetsOnlyThePageContent() throws Exception {
    mvc.perform(get("/households").with(signedInAs(anna)).header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.matchesPattern("(?s)\\s*<section id=\"households\".*")))
        .andExpect(content().string(not(containsString("<html"))));
  }

  @Test
  void theHeaderShowsTheCurrentHouseholdOrTheInvitationToCreateOne() throws Exception {
    mvc.perform(get("/").with(signedInAs(anna)))
        .andExpect(content().string(containsString("id=\"household-slot\"")))
        .andExpect(content().string(containsString(">Annas</a>")));

    long newcomer = HouseholdTestSupport.insertUser(jdbc, "newcomer");
    mvc.perform(get("/").with(signedInAs(newcomer)))
        .andExpect(content().string(containsString("Háztartás létrehozása")));
  }

  @Test
  void theHeaderLinksNothingBeforeSignIn() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(content().string(containsString("id=\"household-slot\"")))
        .andExpect(content().string(not(containsString("/households"))));
  }

  // ---- create ----

  @Test
  void creatingAHouseholdMakesMeOwnerAndSwitchesToIt() throws Exception {
    Cookie session = signInSession(anna);

    mvc.perform(
            post("/households")
                .param("name", "  Summer house ")
                .header("HX-Request", "true")
                .with(signedInAs(anna))
                .with(csrf())
                .cookie(session))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/households"));

    assertThat(service.listFor(anna))
        .filteredOn(h -> h.name().equals("Summer house"))
        .singleElement()
        .satisfies(h -> assertThat(h.role()).isEqualTo(HouseholdRole.OWNER));
    mvc.perform(get("/").with(signedInAs(anna)).cookie(session))
        .andExpect(content().string(containsString(">Summer house</a>")));
  }

  @Test
  void aPlainFormPostIsAnsweredWithARedirect() throws Exception {
    mvc.perform(post("/households").param("name", "Plain").with(signedInAs(anna)).with(csrf()))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/households"));
  }

  @Test
  void aBlankNameGivesTheFormBackWithAnErrorAndCreatesNothing() throws Exception {
    mvc.perform(
            post("/households")
                .param("name", "   ")
                .header("HX-Request", "true")
                .with(signedInAs(anna))
                .with(csrf()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(startsWith("<form id=\"create-form\"")))
        .andExpect(content().string(containsString("1 és 100 karakter")));

    assertThat(service.listFor(anna)).hasSize(2);
  }

  @Test
  void aRejectedPlainPostShowsTheWholePageWithTheTypedTextKept() throws Exception {
    mvc.perform(
            post("/households").param("name", "x".repeat(101)).with(signedInAs(anna)).with(csrf()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("value=\"" + "x".repeat(101) + "\"")));
  }

  @Test
  void aMissingNameIsTreatedAsBlank() throws Exception {
    mvc.perform(post("/households").with(signedInAs(anna)).with(csrf()))
        .andExpect(status().isUnprocessableContent());
  }

  // ---- switch ----

  @Test
  void switchingChangesTheHouseholdTheHeaderShows() throws Exception {
    Cookie session = signInSession(anna);

    mvc.perform(
            post("/households/" + belas + "/switch")
                .header("HX-Request", "true")
                .with(signedInAs(anna))
                .with(csrf())
                .cookie(session))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/"));

    mvc.perform(get("/").with(signedInAs(anna)).cookie(session))
        .andExpect(content().string(containsString(">Belas</a>")));
  }

  @Test
  void switchingToAnotherPeoplesHouseholdIsNotFound() throws Exception {
    Cookie session = signInSession(bela);

    mvc.perform(
            post("/households/" + annas + "/switch")
                .with(signedInAs(bela))
                .with(csrf())
                .cookie(session))
        .andExpect(status().isNotFound());

    mvc.perform(get("/").with(signedInAs(bela)).cookie(session))
        .andExpect(content().string(not(containsString(">Annas</a>"))));
  }

  @Test
  void aHouseholdIHaveBeenRemovedFromStopsWorkingAtOnce() throws Exception {
    Cookie session = signInSession(anna);
    mvc.perform(
        post("/households/" + belas + "/switch")
            .with(signedInAs(anna))
            .with(csrf())
            .cookie(session));
    mvc.perform(get("/").with(signedInAs(anna)).cookie(session))
        .andExpect(content().string(containsString(">Belas</a>")));

    jdbc.update("delete from membership where household_id = ? and user_id = ?", belas, anna);

    mvc.perform(get("/").with(signedInAs(anna)).cookie(session))
        .andExpect(content().string(containsString(">Annas</a>")));
    mvc.perform(
            post("/households/" + belas + "/switch")
                .with(signedInAs(anna))
                .with(csrf())
                .cookie(session))
        .andExpect(status().isNotFound());
  }

  // ---- rename ----

  @Test
  void anOwnerRenames() throws Exception {
    mvc.perform(
            post("/households/" + annas + "/rename")
                .param("name", "Kovács család")
                .header("HX-Request", "true")
                .with(signedInAs(anna))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/households"));

    assertThat(service.require(annas, anna).name()).isEqualTo("Kovács család");
  }

  @Test
  void aMemberCannotRenameAndItIsForbiddenNotHidden() throws Exception {
    mvc.perform(
            post("/households/" + belas + "/rename")
                .param("name", "Hijacked")
                .with(signedInAs(anna))
                .with(csrf()))
        .andExpect(status().isForbidden());

    assertThat(service.require(belas, bela).name()).isEqualTo("Belas");
  }

  @Test
  void renamingAnotherPeoplesHouseholdIsNotFound() throws Exception {
    mvc.perform(
            post("/households/" + annas + "/rename")
                .param("name", "Hijacked")
                .with(signedInAs(bela))
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertThat(service.require(annas, anna).name()).isEqualTo("Annas");
  }

  @Test
  void aBadNameGivesTheRenameFormBack() throws Exception {
    mvc.perform(
            post("/households/" + annas + "/rename")
                .param("name", " ")
                .header("HX-Request", "true")
                .with(signedInAs(anna))
                .with(csrf()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(startsWith("<form id=\"rename-form\"")))
        .andExpect(content().string(containsString("1 és 100 karakter")));
  }

  @Test
  void aMissingNameOnRenameIsTreatedAsBlank() throws Exception {
    mvc.perform(post("/households/" + annas + "/rename").with(signedInAs(anna)).with(csrf()))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void anEditConflictAsksForAReload() throws Exception {
    doThrow(new OptimisticLockingFailureException("stale"))
        .when(spiedService)
        .rename(anyLong(), anyLong(), anyString());

    mvc.perform(
            post("/households/" + annas + "/rename")
                .param("name", "Mine")
                .header("HX-Request", "true")
                .with(signedInAs(anna))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("Valaki más közben átnevezte")))
        .andExpect(content().string(containsString("value=\"Mine\"")));
  }

  @Test
  void everyFormNeedsACsrfToken() throws Exception {
    mvc.perform(post("/households").param("name", "No token").with(signedInAs(anna)))
        .andExpect(status().isForbidden());
  }
}
