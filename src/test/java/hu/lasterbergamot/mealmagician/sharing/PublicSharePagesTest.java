package hu.lasterbergamot.mealmagician.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import hu.lasterbergamot.mealmagician.shared.web.SecurityHeadersFilter;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public share page end to end (PLAN.md D28): what it shows, what it hides, that every bad
 * token gets one identical answer, the privacy headers, that it writes no session or cookie, and
 * that the token never reaches the application log. Anna owns the household with the shared week;
 * Cili owns another household whose meals must never show. The throttle has its own test class.
 */
@SpringBootTest(properties = "mm.share.rate-per-minute=100000")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PublicSharePagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  private static final String NOTE = "private-note-text";

  @Autowired private MockMvc mvc;
  @Autowired private ShareLinkService shares;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private OccasionService occasions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @MockitoSpyBean private ShareLinkService spiedShares;

  private long anna;
  private long annas;
  private long linkId;
  private String token;

  @BeforeEach
  void aSharedWeek() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    long cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    annas = households.create(anna, "Zebra Household").id();
    long cilis = households.create(cili, "Other Household").id();
    long stew = meals.quickAdd(anna, annas, "Goulash stew").id();
    long soup = meals.quickAdd(anna, annas, "Fish soup").id();
    long odd = meals.quickAdd(anna, annas, "<b>Tricky</b> & co").id();
    long foreign = meals.quickAdd(cili, cilis, "Foreign dish").id();
    planning.add(anna, annas, MONDAY, SlotType.LUNCH, stew, NOTE);
    planning.add(anna, annas, MONDAY, SlotType.DINNER, soup, null);
    planning.add(anna, annas, MONDAY.plusDays(2), SlotType.LUNCH, odd, null);
    planning.add(anna, annas, MONDAY.plusDays(7), SlotType.LUNCH, soup, null);
    planning.add(cili, cilis, MONDAY, SlotType.LUNCH, foreign, null);
    occasions.create(
        anna,
        annas,
        new OccasionDetails("Grandma party", MONDAY.plusDays(4), OccasionType.PARTY, "secret"));
    CreatedShareLink created = shares.create(anna, annas, MONDAY.plusDays(3), ShareExpiry.DAYS_7);
    linkId = created.link().id();
    token = created.token();
  }

  private MockHttpServletResponse open(String candidate) throws Exception {
    return mvc.perform(get("/share/" + candidate)).andReturn().getResponse();
  }

  private void expire() {
    jdbc.update(
        "update share_link set created_at = ?, expires_at = ? where id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(3))),
        Timestamp.from(Instant.now().minusSeconds(60)),
        linkId);
    entityManager.clear();
  }

  @Test
  void anActiveLinkShowsTheWeeksMealsSlotTypesDatesAndOccasions() throws Exception {
    MockHttpServletResponse response = open(token);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).startsWith("text/html");
    String body = response.getContentAsString();
    assertThat(body)
        .contains("Goulash stew", "Fish soup", "Grandma party")
        .contains("data-slot=\"LUNCH\"", "data-slot=\"DINNER\"")
        .contains("datetime=\"2026-11-02\"", "datetime=\"2026-11-08\"")
        .contains("45. hét", "Hétfő", "Vasárnap")
        .contains("Nincs tervezve semmi");
  }

  @Test
  void theWeekIsNotDrawnWithSlotTypesThatHoldNoMeal() throws Exception {
    String body = open(token).getContentAsString();

    assertThat(body).doesNotContain("data-slot=\"BREAKFAST\"", "data-slot=\"SNACK\"");
  }

  @Test
  void aMealNameIsEscaped() throws Exception {
    String body = open(token).getContentAsString();

    assertThat(body).contains("&lt;b&gt;Tricky&lt;/b&gt; &amp; co").doesNotContain("<b>Tricky");
  }

  @Test
  void thePageHoldsNothingPrivate() throws Exception {
    String body = open(token).getContentAsString();

    assertThat(body)
        .doesNotContain(
            NOTE,
            "secret",
            "Zebra Household",
            "Other Household",
            "anna@example.com",
            "_csrf",
            "<form",
            "Foreign dish")
        .doesNotContain(token);
  }

  @Test
  void aSlotOfAnotherWeekIsNotOnThePage() throws Exception {
    // "Fish soup" is planned for next Monday as well, so count the appearances: only Monday's.
    String body = open(token).getContentAsString();

    assertThat(body.split("Fish soup", -1)).hasSize(2);
  }

  @Test
  void theEnglishPageUsesTheLanguageCookie() throws Exception {
    String body =
        mvc.perform(get("/share/" + token).cookie(new jakarta.servlet.http.Cookie("mm_lang", "en")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("Week 45", "Monday", "Nothing planned");
  }

  @Test
  void aMalformedTokenIsRefusedWithoutAskingTheService() throws Exception {
    MockHttpServletResponse response = open("short");

    assertThat(response.getStatus()).isEqualTo(404);
    verify(spiedShares, never()).open(any());
  }

  @Test
  void unknownExpiredRevokedAndMalformedTokensGetTheSameAnswer() throws Exception {
    String unknown = TokenCodes.generate();
    MockHttpServletResponse unknownResponse = open(unknown);
    MockHttpServletResponse malformed = open("not-a-token");
    expire();
    MockHttpServletResponse expired = open(token);
    CreatedShareLink second = shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);
    shares.revoke(anna, annas, second.link().id());
    MockHttpServletResponse revoked = open(second.token());

    assertThat(unknownResponse.getStatus()).isEqualTo(404);
    for (MockHttpServletResponse other : List.of(malformed, expired, revoked)) {
      assertThat(other.getStatus()).isEqualTo(404);
      assertThat(other.getContentAsString()).isEqualTo(unknownResponse.getContentAsString());
      assertThat(headersOf(other)).isEqualTo(headersOf(unknownResponse));
    }
    assertThat(unknownResponse.getContentAsString())
        .doesNotContain(unknown, "Goulash stew")
        .contains("Ez a link nem működik");
  }

  @Test
  void theWeekAnswersWithThePrivacyHeaders() throws Exception {
    assertPrivacyHeaders(open(token));
  }

  @Test
  void aRefusalAnswersWithThePrivacyHeadersToo() throws Exception {
    assertPrivacyHeaders(open(TokenCodes.generate()));
    assertPrivacyHeaders(open("bad"));
  }

  private static void assertPrivacyHeaders(MockHttpServletResponse response) {
    assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow, noarchive");
    assertThat(response.getHeaders("Referrer-Policy")).containsExactly("no-referrer");
    assertThat(response.getHeader("Cache-Control")).contains("no-store");
    assertThat(response.getHeader("Content-Security-Policy"))
        .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY);
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void theVisitSetsNoCookieAndCreatesNoSession() throws Exception {
    Integer before = jdbc.queryForObject("select count(*) from spring_session", Integer.class);

    MockHttpServletResponse ok = open(token);
    MockHttpServletResponse refused = open(TokenCodes.generate());

    for (MockHttpServletResponse response : List.of(ok, refused)) {
      assertThat(response.getHeaders("Set-Cookie")).isEmpty();
      assertThat(response.getCookies()).isEmpty();
    }
    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class))
        .isEqualTo(before);
  }

  @Test
  void thePageLoadsNoServiceWorkerManifestOrHtmx() throws Exception {
    String body = open(token).getContentAsString();

    assertThat(body)
        .contains("<meta name=\"robots\" content=\"noindex,nofollow,noarchive\">")
        .doesNotContain("sw-register", "manifest", "/js/htmx", "install-hint", "<script>", "csrf")
        .contains("<script src=\"/js/app.js\" defer>", "alpine-csp.min.js");
  }

  @Test
  void theTokenNeverReachesTheApplicationLog() throws Exception {
    Logger app = (Logger) LoggerFactory.getLogger("hu.lasterbergamot.mealmagician");
    Level before = app.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(appender);
    // The most talkative setting for our own code: if any class logged the token, it would show.
    app.setLevel(Level.TRACE);
    List<String> tokens = new ArrayList<>();
    try {
      String unknown = TokenCodes.generate();
      String revokedToken = shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7).token();
      shares.revoke(anna, annas, latestLinkId());
      tokens.addAll(List.of(token, unknown, revokedToken));
      // Proves the appender really hears this package, so an empty result below means "no leak".
      LoggerFactory.getLogger("hu.lasterbergamot.mealmagician.sharing.internal")
          .debug("capture check");
      open(token);
      open(unknown);
      open(revokedToken);
      open("malformed-token");
      expire();
      open(token);
    } finally {
      app.setLevel(before);
      root.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .contains("capture check");
    for (ILoggingEvent event : appender.list) {
      String line = event.getFormattedMessage() + " " + event.getThrowableProxy();
      for (String secret : tokens) {
        assertThat(line).doesNotContain(secret);
      }
      assertThat(line).doesNotContain("malformed-token");
    }
  }

  private long latestLinkId() {
    return jdbc.queryForObject("select max(id) from share_link", Long.class);
  }

  @Test
  void robotsTxtIsPublicAndDisallowsEverything() throws Exception {
    MockHttpServletResponse response = mvc.perform(get("/robots.txt")).andReturn().getResponse();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).startsWith("text/plain");
    assertThat(response.getContentAsString()).contains("User-agent: *").contains("Disallow: /");
  }

  private static Map<String, List<String>> headersOf(MockHttpServletResponse response) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      headers.put(name, response.getHeaders(name));
    }
    return headers;
  }
}
