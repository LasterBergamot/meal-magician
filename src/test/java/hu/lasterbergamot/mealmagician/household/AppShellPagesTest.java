package hu.lasterbergamot.mealmagician.household;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.shared.web.Theme;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The app shell as a signed-in person sees it, through the whole stack: which navigation is drawn,
 * which tab is highlighted, which households the switcher lists, the sign-out form and the stored
 * language. Anna owns "Annas" and is a member of "Belas"; Bela owns "Belas" and "Titkos háztartás",
 * which Anna must never see in her switcher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AppShellPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private long anna;
  private long bela;
  private long annas;
  private long belas;
  private long secret;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annas = service.create(anna, "Annas").id();
    belas = service.create(bela, "Belas").id();
    secret = service.create(bela, "Titkos háztartás").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', ?)",
        belas,
        anna,
        OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
  }

  @Test
  void aSignedInPageIsDrawnInsideTheShellWithBothNavigations() throws Exception {
    mvc.perform(get("/").with(signedInAs(anna)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"shell\"")))
        .andExpect(content().string(containsString("id=\"sidebar\"")))
        .andExpect(content().string(containsString("id=\"household-sheet-phone\"")))
        .andExpect(content().string(containsString("id=\"household-sheet-desktop\"")))
        .andExpect(content().string(containsString("aria-label=\"Fő navigáció\"")));
  }

  @Test
  void bothThemeControlsMarkTheChosenThemeFromTheCookie() throws Exception {
    String page =
        mvc.perform(get("/").with(signedInAs(anna)).cookie(new Cookie(Theme.COOKIE_NAME, "dark")))
            .andExpect(content().string(containsString("data-theme=\"dark\"")))
            .andExpect(content().string(containsString("id=\"theme-switch-phone\"")))
            .andExpect(content().string(containsString("id=\"theme-switch-desktop\"")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Each of the two controls has one pressed button: the one for "dark".
    assertThat(page.split("data-theme-choice=\"dark\" aria-pressed=\"true\"", -1)).hasSize(3);
    assertThat(page.split("aria-pressed=\"true\"", -1)).hasSize(3);
    assertThat(page.split("data-theme-choice=\"light\" aria-pressed=\"false\"", -1)).hasSize(3);
  }

  @Test
  void theCurrentDestinationIsHighlightedAndOnlyExistingPagesAreLinked() throws Exception {
    mvc.perform(get("/households").with(signedInAs(anna)))
        .andExpect(content().string(containsString("href=\"/households\"")))
        .andExpect(content().string(containsString("aria-current=\"page\"")))
        .andExpect(content().string(containsString("href=\"/plan\"")))
        .andExpect(content().string(containsString("href=\"/meals\"")))
        .andExpect(content().string(not(containsString("href=\"/occasions\""))));
  }

  @Test
  void theSwitcherListsOnlyMyHouseholdsAndOffersASwitchForEachOtherOne() throws Exception {
    mvc.perform(get("/").with(signedInAs(anna)))
        .andExpect(content().string(containsString("Annas")))
        .andExpect(content().string(containsString("action=\"/households/" + belas + "/switch\"")))
        .andExpect(
            content().string(not(containsString("action=\"/households/" + annas + "/switch\""))))
        .andExpect(
            content().string(not(containsString("action=\"/households/" + secret + "/switch\""))))
        .andExpect(content().string(not(containsString("Titkos háztartás"))));
  }

  @Test
  void theSignOutButtonIsAPostWithTheCsrfField() throws Exception {
    mvc.perform(get("/").with(signedInAs(anna)))
        .andExpect(content().string(containsString("method=\"post\" action=\"/logout\"")))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
  }

  @Test
  void aLanguageChosenWhileSignedInIsSavedOnThePerson() throws Exception {
    mvc.perform(
            post("/language")
                .with(signedInAs(anna))
                .with(csrf())
                .header("Referer", "http://localhost/households")
                .param("lang", "en"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/households"))
        .andExpect(cookie().value("mm_lang", "en"));

    // The change is made on the entity, which Hibernate writes at the next flush; the plain JDBC
    // query below would not see it before that, so flush first.
    em.flush();
    assertThat(jdbc.queryForObject("select locale from app_user where id = ?", String.class, anna))
        .isEqualTo("en");
  }

  @Test
  void aGetWithALangParameterDoesNotChangeTheStoredLanguage() throws Exception {
    mvc.perform(get("/").with(signedInAs(anna)).param("lang", "en"))
        .andExpect(content().string(containsString("<html lang=\"hu\"")));

    em.flush();
    assertThat(jdbc.queryForObject("select locale from app_user where id = ?", String.class, anna))
        .isNotEqualTo("en");
  }

  @Test
  void aLanguageSwitchWithoutTheCsrfTokenIsRefused() throws Exception {
    mvc.perform(post("/language").with(signedInAs(anna)).param("lang", "en"))
        .andExpect(status().isForbidden());
  }

  @Test
  void theLanguageButtonsArePostFormsWithTheCsrfField() throws Exception {
    mvc.perform(get("/").with(signedInAs(anna)))
        .andExpect(content().string(containsString("method=\"post\" action=\"/language\"")))
        .andExpect(content().string(containsString("name=\"lang\" value=\"en\"")))
        .andExpect(content().string(not(containsString("href=\"?lang="))));
  }

  @Test
  void anAnonymousPageHasTheLanguageChoiceAndNoShell() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(content().string(containsString("Nyelv")))
        .andExpect(content().string(not(containsString("id=\"shell\""))))
        .andExpect(content().string(not(containsString("id=\"sidebar\""))));
  }
}
