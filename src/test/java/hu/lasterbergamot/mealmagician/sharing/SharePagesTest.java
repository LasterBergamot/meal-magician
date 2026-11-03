package hu.lasterbergamot.mealmagician.sharing;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The share-link management routes through the whole stack (PLAN.md D28): creating a link and
 * seeing it once, the list, the cap, revoking, and the 404 for another household's link. Anna owns
 * "Annas", Bela is a plain member of it, and Cili owns "Cilis". Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SharePagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  private static final Pattern LINK = Pattern.compile("value=\"http://localhost/share/([^\"]+)\"");

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private ShareLinkService shares;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long cili;
  private long annas;
  private long cilis;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    annas = households.create(anna, "Annas").id();
    cilis = households.create(cili, "Cilis").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
        annas,
        bela);
  }

  private ResultActions as(long userId, MockHttpServletRequestBuilder request, boolean htmx)
      throws Exception {
    MockHttpServletRequestBuilder built = request.with(signedInAs(userId)).with(csrf());
    return mvc.perform(htmx ? built.header("HX-Request", "true") : built);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private String createdToken(String html) {
    Matcher match = LINK.matcher(html);
    assertThat(match.find()).as("the response shows the new link").isTrue();
    return match.group(1);
  }

  @Test
  void creatingALinkShowsTheFullAddressOnceWithANoStoreHeader() throws Exception {
    ResultActions result =
        as(anna, post("/plan/share").param("week", "2026-W45").param("expiry", "7d"), true)
            .andExpect(status().isOk());

    MockHttpServletResponse response = result.andReturn().getResponse();
    String token = createdToken(response.getContentAsString());
    assertThat(token).hasSize(43);
    assertThat(response.getHeader("Cache-Control")).contains("no-store");
    assertThat(response.getHeader("Vary")).contains("HX-Request");
    assertThat(response.getContentAsString()).doesNotContain("<html");
    assertThat(shares.open(token)).isPresent();
  }

  @Test
  void theDatabaseRowHoldsOnlyTheHashAndTheTokenNeverLeavesTheBody() throws Exception {
    MockHttpServletResponse response =
        as(anna, post("/plan/share").param("week", "2026-W45"), true).andReturn().getResponse();

    String token = createdToken(response.getContentAsString());
    String stored = jdbc.queryForObject("select token_hash from share_link", String.class);
    assertThat(stored).isEqualTo(TokenCodes.hash(token)).doesNotContain(token);
    assertThat(response.getHeaderNames())
        .noneMatch(name -> response.getHeaders(name).stream().anyMatch(v -> v.contains(token)));
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void theListThatFollowsNeverShowsTheTokenAgain() throws Exception {
    String token =
        createdToken(body(as(anna, post("/plan/share").param("week", "2026-W45"), true)));

    String html = body(as(anna, get("/plan/share").param("week", "2026-W45"), true));

    assertThat(html).doesNotContain(token).doesNotContain(TokenCodes.hash(token));
    assertThat(html).contains("Aktív").contains("készítette: anna");
  }

  @Test
  void anUnknownExpiryMakesASevenDayLink() throws Exception {
    as(anna, post("/plan/share").param("week", "2026-W45").param("expiry", "forever"), true)
        .andExpect(status().isOk());

    List<ShareLinkView> links = shares.list(anna, annas, MONDAY);
    assertThat(links).hasSize(1);
    assertThat(links.get(0).expiresAt())
        .isBetween(
            links.get(0).createdAt().plus(ShareExpiry.DAYS_7.duration()).minusSeconds(1),
            links.get(0).createdAt().plus(ShareExpiry.DAYS_7.duration()).plusSeconds(1));
  }

  @Test
  void theChosenExpiryIsUsed() throws Exception {
    as(anna, post("/plan/share").param("week", "2026-W45").param("expiry", "1d"), true);

    ShareLinkView link = shares.list(anna, annas, MONDAY).get(0);
    assertThat(link.expiresAt()).isBefore(link.createdAt().plus(ShareExpiry.DAYS_7.duration()));
  }

  @Test
  void aPlainMemberMayCreateALinkToo() throws Exception {
    as(bela, post("/plan/share").param("week", "2026-W45"), true).andExpect(status().isOk());

    assertThat(shares.list(anna, annas, MONDAY)).hasSize(1);
  }

  @Test
  void aNormalRequestGetsTheWholePageWithTheLink() throws Exception {
    String html = body(as(anna, post("/plan/share").param("week", "2026-W45"), false));

    assertThat(html).contains("<html").contains("Kész a linked");
    assertThat(createdToken(html)).hasSize(43);
  }

  @Test
  void theSheetContentAndThePageListTheWeeksLinks() throws Exception {
    shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);

    String fragment = body(as(anna, get("/plan/share").param("week", "2026-W45"), true));
    String page = body(as(anna, get("/plan/share").param("week", "2026-W45"), false));

    assertThat(fragment).contains("id=\"share-links\"").doesNotContain("<html");
    assertThat(page).contains("<html").contains("id=\"share-links\"");
    assertThat(fragment).contains("45. hét");
  }

  @Test
  void aWeekWithoutLinksShowsTheEmptyMessage() throws Exception {
    String html = body(as(anna, get("/plan/share").param("week", "2026-W46"), true));

    assertThat(html).contains("Ehhez a héthez még nincs link.");
  }

  @Test
  void aBadWeekCodeShowsTheCurrentWeeksSheet() throws Exception {
    as(anna, get("/plan/share").param("week", "garbage"), true).andExpect(status().isOk());
  }

  @Test
  void anEleventhActiveLinkIsRefusedWithTheMessageAndStatus422() throws Exception {
    for (int i = 0; i < ShareLinkService.MAX_ACTIVE; i++) {
      shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);
    }

    ResultActions result =
        as(anna, post("/plan/share").param("week", "2026-W45").param("expiry", "30d"), true)
            .andExpect(status().isUnprocessableContent());

    String html = body(result);
    assertThat(html).contains("már 10 aktív linkje van").contains("id=\"share-panel\"");
    assertThat(html).contains("<option value=\"30d\" selected");
    assertThat(LINK.matcher(html).find()).isFalse();
    assertThat(shares.list(anna, annas, MONDAY)).hasSize(ShareLinkService.MAX_ACTIVE);
  }

  @Test
  void revokingStopsTheLinkAtOnceAndReturnsTheRefreshedList() throws Exception {
    CreatedShareLink created = shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);

    String html =
        body(
            as(
                bela,
                post("/plan/share/" + created.link().id() + "/revoke").param("week", "2026-W45"),
                true));

    assertThat(shares.open(created.token())).isEmpty();
    assertThat(shares.list(anna, annas, MONDAY).get(0).status()).isEqualTo(ShareLinkStatus.REVOKED);
    assertThat(html).contains("id=\"share-links\"").contains("Visszavonva").doesNotContain("<html");
    assertThat(html).doesNotContain("share-revoke-");
  }

  @Test
  void aNormalRevokeRequestGetsTheWholePage() throws Exception {
    CreatedShareLink created = shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);

    String html =
        body(
            as(
                anna,
                post("/plan/share/" + created.link().id() + "/revoke").param("week", "2026-W45"),
                false));

    assertThat(html).contains("<html").contains("Visszavonva");
  }

  @Test
  void revokingAnotherHouseholdsLinkIsNotFoundAndLeavesItWorking() throws Exception {
    CreatedShareLink created = shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);

    as(cili, post("/plan/share/" + created.link().id() + "/revoke"), true)
        .andExpect(status().isNotFound());

    assertThat(shares.open(created.token())).isPresent();
  }

  @Test
  void anotherHouseholdsLinksAreNeverListed() throws Exception {
    shares.create(cili, cilis, MONDAY, ShareExpiry.DAYS_7);

    String html = body(as(anna, get("/plan/share").param("week", "2026-W45"), true));

    assertThat(html).contains("Ehhez a héthez még nincs link.").doesNotContain("készítette: cili");
  }

  @Test
  void aLinkWhoseCreatorLeftShowsAFormerMember() throws Exception {
    shares.create(bela, annas, MONDAY, ShareExpiry.DAYS_7);
    jdbc.update("delete from membership where user_id = ?", bela);

    String html = body(as(anna, get("/plan/share").param("week", "2026-W45"), true));

    assertThat(html).contains("egykori tag");
  }
}
