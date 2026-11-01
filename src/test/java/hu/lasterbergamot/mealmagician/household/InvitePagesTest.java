package hu.lasterbergamot.mealmagician.household;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The invite section and the join page through the whole stack. Anna owns "Annas", Bela is a plain
 * member of it, Cili owns "Cilis", Gabi is the person being invited and Dora belongs nowhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InvitePagesTest {

  private static final String GABI_EMAIL = "gabi@example.com";
  private static final Pattern LINK = Pattern.compile("/join/([A-Za-z0-9_-]+)");

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private InviteService invites;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private long anna;
  private long bela;
  private long cili;
  private long gabi;
  private long dora;
  private long annas;
  private long cilis;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    gabi = HouseholdTestSupport.insertUser(jdbc, "gabi");
    dora = HouseholdTestSupport.insertUser(jdbc, "dora");
    annas = households.create(anna, "Annas").id();
    cilis = households.create(cili, "Cilis").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
        annas,
        bela);
  }

  private Cookie sessionOf(long userId, String email) throws Exception {
    Cookie cookie =
        mvc.perform(get("/households").with(signedInAs(userId, email)))
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private ResultActions createAsAnna(String email, String expiry, boolean htmx) throws Exception {
    var request =
        post("/households/invites")
            .with(signedInAs(anna, "anna@example.com"))
            .with(csrf())
            .param("email", email)
            .param("expiry", expiry);
    if (htmx) {
      request = request.header("HX-Request", "true");
    }
    return mvc.perform(request);
  }

  private String codeIn(ResultActions result) throws Exception {
    Matcher matcher = LINK.matcher(result.andReturn().getResponse().getContentAsString());
    assertThat(matcher.find()).as("the response contains a join link").isTrue();
    return matcher.group(1);
  }

  private String newCode() {
    return invites.create(annas, anna, GABI_EMAIL, InviteExpiry.HOURS_24).code();
  }

  @Test
  void onlyTheOwnerSeesTheInviteSection() throws Exception {
    mvc.perform(get("/households").with(signedInAs(anna, "anna@example.com")))
        .andExpect(content().string(containsString("id=\"invites\"")))
        .andExpect(content().string(containsString("Még nincs meghívó")));
    mvc.perform(get("/households").with(signedInAs(bela, "bela@example.com")))
        .andExpect(content().string(not(containsString("id=\"invites\""))));
  }

  @Test
  void creatingAnInviteShowsTheLinkOnceAndListsTheInvite() throws Exception {
    ResultActions created = createAsAnna(GABI_EMAIL, "2h", true);

    created
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("http://localhost/join/")))
        .andExpect(content().string(containsString(GABI_EMAIL)))
        .andExpect(content().string(containsString("Függőben")))
        .andExpect(content().string(containsString("Visszavonás")))
        .andExpect(content().string(not(containsString("<html"))))
        .andExpect(header().string("Vary", "HX-Request"));
    String code = codeIn(created);

    mvc.perform(get("/households").with(signedInAs(anna, "anna@example.com")))
        .andExpect(content().string(not(containsString(code))))
        .andExpect(content().string(containsString(GABI_EMAIL)));
  }

  @Test
  void aNormalRequestGetsTheWholePageWithTheLink() throws Exception {
    createAsAnna(GABI_EMAIL, "24h", false)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("/join/")));
  }

  @Test
  void aBadAddressKeepsTheFormAndExplainsWhy() throws Exception {
    createAsAnna("not-an-address", "3d", true)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Adj meg egy érvényes e-mail-címet")))
        .andExpect(content().string(containsString("value=\"not-an-address\"")))
        .andExpect(content().string(not(containsString("/join/"))));
  }

  @Test
  void aPlainMemberMayNotInvite() throws Exception {
    mvc.perform(
            post("/households/invites")
                .with(signedInAs(bela, "bela@example.com"))
                .with(csrf())
                .param("email", GABI_EMAIL))
        .andExpect(status().isForbidden());
  }

  @Test
  void aPersonWithoutAHouseholdGetsNotFound() throws Exception {
    mvc.perform(
            post("/households/invites")
                .with(signedInAs(dora, "dora@example.com"))
                .with(csrf())
                .param("email", GABI_EMAIL))
        .andExpect(status().isNotFound());
  }

  @Test
  void revokingShowsTheInviteAsRevoked() throws Exception {
    long id = invites.create(annas, anna, GABI_EMAIL, InviteExpiry.HOURS_24).invite().id();

    mvc.perform(
            post("/households/invites/" + id + "/revoke")
                .with(signedInAs(anna, "anna@example.com"))
                .with(csrf())
                .header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Visszavonva")))
        .andExpect(content().string(not(containsString("/revoke"))));
  }

  @Test
  void anotherHouseholdsInviteIsNotFoundAndStaysPending() throws Exception {
    long ciliInvite = invites.create(cilis, cili, GABI_EMAIL, InviteExpiry.HOURS_24).invite().id();

    mvc.perform(
            post("/households/invites/" + ciliInvite + "/revoke")
                .with(signedInAs(anna, "anna@example.com"))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(invites.list(cilis, cili))
        .extracting(InviteView::status)
        .containsExactly(InviteStatus.PENDING);
  }

  @Test
  void theJoinPageNeedsSignIn() throws Exception {
    mvc.perform(get("/join/anything"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void theInvitedPersonSeesTheHouseholdAndAJoinButton() throws Exception {
    String code = newCode();

    mvc.perform(get("/join/" + code).with(signedInAs(gabi, GABI_EMAIL)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Meghívót kaptál")))
        .andExpect(content().string(containsString("Annas")))
        .andExpect(content().string(containsString("hx-post=\"/join\"")))
        .andExpect(content().string(containsString("value=\"" + code + "\"")));
  }

  @Test
  void openingTheLinkDoesNotUseTheInviteUp() throws Exception {
    String code = newCode();

    mvc.perform(get("/join/" + code).with(signedInAs(gabi, GABI_EMAIL)));

    assertThat(households.find(annas, gabi)).isEmpty();
    assertThat(invites.list(annas, anna))
        .extracting(InviteView::status)
        .containsExactly(InviteStatus.PENDING);
  }

  @Test
  void joiningMakesThePersonAMemberAndSwitchesToTheHousehold() throws Exception {
    String code = newCode();
    Cookie session = sessionOf(gabi, GABI_EMAIL);

    mvc.perform(
            post("/join")
                .with(signedInAs(gabi, GABI_EMAIL))
                .with(csrf())
                .cookie(session)
                .header("HX-Request", "true")
                .param("code", code))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/households"));

    assertThat(households.require(annas, gabi).role()).isEqualTo(HouseholdRole.MEMBER);
    mvc.perform(get("/households").with(signedInAs(gabi, GABI_EMAIL)).cookie(session))
        .andExpect(content().string(containsString("aria-current=\"true\"")))
        .andExpect(content().string(containsString("Annas")));
  }

  @Test
  void aNormalJoinRequestRedirectsWithSeeOther() throws Exception {
    mvc.perform(
            post("/join").with(signedInAs(gabi, GABI_EMAIL)).with(csrf()).param("code", newCode()))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/households"));
  }

  @Test
  void aLinkThatStoppedWorkingSendsThePersonBackToTheExplanation() throws Exception {
    mvc.perform(
            post("/join")
                .with(signedInAs(gabi, GABI_EMAIL))
                .with(csrf())
                .header("HX-Request", "true")
                .param("code", "bad code/"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/join/bad%20code%2F"));
    assertThat(households.listFor(gabi)).isEmpty();
  }

  @Test
  void anUnknownLinkIsNotFound() throws Exception {
    mvc.perform(get("/join/nope").with(signedInAs(gabi, GABI_EMAIL)))
        .andExpect(status().isNotFound())
        .andExpect(content().string(containsString("Ez a meghívó link nem létezik")));
  }

  @Test
  void anotherGoogleAccountIsForbidden() throws Exception {
    mvc.perform(get("/join/" + newCode()).with(signedInAs(dora, "dora@example.com")))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("másik Google-fióknak szól")))
        .andExpect(content().string(not(containsString("Annas"))));
  }

  @Test
  void aRevokedUsedOrExpiredLinkIsGone() throws Exception {
    CreatedInvite revoked = invites.create(annas, anna, GABI_EMAIL, InviteExpiry.HOURS_24);
    invites.revoke(annas, anna, revoked.invite().id());
    mvc.perform(get("/join/" + revoked.code()).with(signedInAs(gabi, GABI_EMAIL)))
        .andExpect(status().isGone())
        .andExpect(content().string(containsString("visszavonta")));

    String used = newCode();
    invites.join(used, gabi, GABI_EMAIL);
    mvc.perform(get("/join/" + used).with(signedInAs(gabi, GABI_EMAIL)))
        .andExpect(status().isGone())
        .andExpect(content().string(containsString("már fel lett használva")));

    CreatedInvite expired = invites.create(annas, anna, GABI_EMAIL, InviteExpiry.HOURS_2);
    jdbc.update(
        "update invite set expires_at = ? where id = ?",
        Timestamp.from(Instant.now().minusSeconds(60)),
        expired.invite().id());
    entityManager.clear();
    mvc.perform(get("/join/" + expired.code()).with(signedInAs(gabi, GABI_EMAIL)))
        .andExpect(status().isGone())
        .andExpect(content().string(containsString("lejárt")));
  }
}
