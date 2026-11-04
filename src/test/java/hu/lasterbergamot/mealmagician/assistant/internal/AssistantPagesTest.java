package hu.lasterbergamot.mealmagician.assistant.internal;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The wizard's routes through the whole stack with the scripted model of the {@code fake-ai}
 * profile: nothing here calls Google. The model's tool calls run on another thread, so the data
 * must be committed; the test is therefore not {@code @Transactional} and removes its rows
 * afterwards. Anna owns "Annas" and Bela "Belas"; Bela must never reach Anna's drafts. The week is
 * a fixed one far from today (Monday 2020-05-11, week 20). Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "fake-ai"})
class AssistantPagesTest {

  private static final String WEEK = "2020-W20";
  private static final LocalDate MONDAY = LocalDate.of(2020, 5, 11);
  private static final Pattern DRAFT_ID = Pattern.compile("/assistant/drafts/(\\d+)/refine");
  private static final Pattern FIRST_ITEM_ID = Pattern.compile("/items/(\\d+)/remove");
  private static final Pattern IDEA_ITEM_ID = Pattern.compile("/items/(\\d+)/idea");

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private UsageLedger ledger;
  @Autowired private ScriptedChatModel model;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;

  @BeforeEach
  void people() {
    model.reset();
    anna = HouseholdTestSupport.insertUser(jdbc, "wiz-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "wiz-bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    meal("Gulyás");
    meal("Paprikás csirke");
    meal("Töltött káposzta");
  }

  @AfterEach
  void cleanUp() {
    for (long home : new long[] {annasHome, belasHome}) {
      jdbc.update("delete from assistant_draft where household_id = ?", home);
      jdbc.update("delete from assistant_usage where household_id = ?", home);
      jdbc.update("delete from meal_slot where household_id = ?", home);
      jdbc.update("delete from household where id = ?", home);
    }
    jdbc.update("delete from app_user where id in (?, ?)", anna, bela);
  }

  private MealView meal(String name) {
    return meals.create(anna, annasHome, new MealDetails(name, null, null, List.of()));
  }

  private ResultActions as(long user, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(user)).with(csrf()).header("HX-Request", "true"));
  }

  private ResultActions asAnna(MockHttpServletRequestBuilder request) throws Exception {
    return as(anna, request);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  /** Makes a draft for week 20 and returns its page. */
  private String draftPage(String... hints) throws Exception {
    var request = post("/assistant/drafts").param("week", WEEK).param("request", "vendégek");
    for (String hint : hints) {
      request = request.param("hint", hint);
    }
    return body(asAnna(request).andExpect(status().isOk()));
  }

  private static long draftId(String page) {
    Matcher match = DRAFT_ID.matcher(page);
    assertThat(match.find()).as("the draft page has its refine form").isTrue();
    return Long.parseLong(match.group(1));
  }

  private static long firstGroup(Pattern pattern, String page) {
    Matcher match = pattern.matcher(page);
    assertThat(match.find()).as(pattern.pattern()).isTrue();
    return Long.parseLong(match.group(1));
  }

  // ---- the entry on the plan page ----

  @Test
  void thePlanPageLoadsTheWizardsEntryOverHtmx() throws Exception {
    String page =
        body(
            mvc.perform(get("/plan").param("week", WEEK).with(signedInAs(anna)))
                .andExpect(status().isOk()));

    assertThat(page)
        .contains("hx-get=\"/assistant/entry?week=" + WEEK + "\"")
        .contains("hx-trigger=\"load\"")
        .contains("hx-get=\"/assistant/notice\"")
        // Nothing of the wizard is drawn by the plan page itself.
        .doesNotContain("id=\"assistant-sheet\"")
        .doesNotContain("Heti javaslat");
  }

  @Test
  void theEntryHasTheButtonTheSheetAndAHintForAnEmptyWeek() throws Exception {
    String entry =
        body(asAnna(get("/assistant/entry").param("week", WEEK)).andExpect(status().isOk()));

    assertThat(entry)
        .contains("id=\"plan-assistant-week\"")
        .contains("data-sheet=\"assistant-sheet\"")
        .contains("hx-get=\"/assistant/week?week=" + WEEK + "\"")
        .contains("id=\"assistant-sheet\"")
        .contains("Heti javaslat")
        .contains("Még üres ez a hét")
        .contains("data-icon=\"wand\"");
  }

  @Test
  void aPlannedWeekHasNoHint() throws Exception {
    planning.add(
        anna,
        annasHome,
        MONDAY,
        SlotType.LUNCH,
        meals.search(anna, annasHome, new MealSearch("", null, null, false)).getFirst().id(),
        null);

    String entry =
        body(asAnna(get("/assistant/entry").param("week", WEEK)).andExpect(status().isOk()));

    assertThat(entry).contains("id=\"plan-assistant-week\"").doesNotContain("Még üres ez a hét");
  }

  @Test
  void aWeekThatIsNotARealWeekMeansTheCurrentWeek() throws Exception {
    String entry =
        body(asAnna(get("/assistant/entry").param("week", "banana")).andExpect(status().isOk()));
    String missing = body(asAnna(get("/assistant/entry")).andExpect(status().isOk()));

    assertThat(entry).doesNotContain("banana").contains("hx-get=\"/assistant/week?week=");
    assertThat(missing).contains("hx-get=\"/assistant/week?week=");
  }

  // ---- step 1: the request ----

  @Test
  void theRequestStepHasTheFieldTheFourChipsAndTheProviderNote() throws Exception {
    String sheet =
        body(asAnna(get("/assistant/week").param("week", WEEK)).andExpect(status().isOk()));

    assertThat(sheet)
        .contains("hx-post=\"/assistant/drafts\"")
        .contains("name=\"week\" value=\"" + WEEK + "\"")
        .contains("<textarea id=\"assistant-request\" name=\"request\"")
        .contains("Van valami különleges ezen a héten?")
        .contains("A kérésedet a Google Gemini dolgozza fel.")
        .contains("Kérem a javaslatot")
        .contains("data-abort-on-close")
        .contains("Mégse");
    for (String chip : List.of("lessMeat", "quick", "vegStew", "leftovers")) {
      assertThat(sheet).contains("name=\"hint\" value=\"" + chip + "\"");
    }
  }

  // ---- step 3: the draft ----

  @Test
  void aDraftIsShownByDayWithLibraryMealsAsRowsAndTheIdeaAsADashedCard() throws Exception {
    String page = draftPage();

    assertThat(page)
        .contains("id=\"assistant-draft\"")
        .contains("Javaslat: ")
        .contains("hétfő")
        .contains("Gulyás")
        .contains("Tökkrémleves")
        .contains("data-idea")
        .contains("border-dashed")
        .contains("Ötlet")
        .contains("Felvétel az ételek közé")
        .contains("Az ételeid közül: 3 · Új ötlet: 1")
        .contains("Változtatnál valamin?")
        .contains("Hozzáadás a tervhez")
        .contains("id=\"assistant-replace-confirm\"")
        .contains("Lecseréled a teljes tervet erre a hétre:");
    assertThat(model.requests()).singleElement().isEqualTo("vendégek");
  }

  @Test
  void nothingReachesThePlanBeforeAccept() throws Exception {
    draftPage();

    assertThat(planning.week(anna, annasHome, MONDAY).slots()).isEmpty();
  }

  @Test
  void theQuickRequestChipsAddTheirPhraseInTheMembersLanguage() throws Exception {
    draftPage("lessMeat", "bogus", "quick");

    assertThat(model.requests().getLast())
        .isEqualTo("vendégek. Kevesebb hús. Gyors ételek")
        .doesNotContain("bogus");
  }

  @Test
  void theEnglishPhrasesComeFromTheEnglishBundle() throws Exception {
    asAnna(
            post("/assistant/drafts")
                .param("week", WEEK)
                .param("hint", "vegStew")
                .param("hint", "leftovers")
                .cookie(new Cookie("mm_lang", "en")))
        .andExpect(status().isOk());

    assertThat(model.requests().getLast()).isEqualTo("More vegetable stews. Leftover-friendly");
  }

  @Test
  void everyQuickRequestPhraseIsShortInBothLanguages() {
    var hu = bundle("messages_hu.properties");
    var en = bundle("messages_en.properties");
    for (String key : QuickHints.KEYS) {
      assertThat(hu.getProperty("assistant.hint." + key)).hasSizeBetween(1, 50);
      assertThat(en.getProperty("assistant.hint." + key)).hasSizeBetween(1, 50);
    }
  }

  @Test
  void aRequestOfExactlyAThousandCharactersIsAccepted() throws Exception {
    asAnna(post("/assistant/drafts").param("week", WEEK).param("request", "a".repeat(1000)))
        .andExpect(status().isOk());

    assertThat(model.requests()).hasSize(1);
  }

  @Test
  void aRequestOverAThousandCharactersIsRefusedOnTheFieldAndNothingIsSent() throws Exception {
    String text = "b".repeat(1001);

    String page =
        body(
            asAnna(
                    post("/assistant/drafts")
                        .param("week", WEEK)
                        .param("request", text)
                        .param("hint", "quick"))
                .andExpect(status().isUnprocessableContent()));

    assertThat(page)
        .contains("Legfeljebb 1000 karakter lehet.")
        .contains("aria-invalid=\"true\"")
        .contains(text)
        .contains("name=\"hint\" value=\"quick\" checked");
    assertThat(model.requests()).isEmpty();
  }

  @Test
  void theSpendCapIsAWarningAndMakesNoDraft() throws Exception {
    ledger.record(annasHome, 10_000_000, 10_000_000);

    String page =
        body(
            asAnna(post("/assistant/drafts").param("week", WEEK).param("request", "vendégek"))
                .andExpect(status().isTooManyRequests()));

    assertThat(page)
        .contains("data-kind=\"warning\"")
        .contains("Elfogyott a varázsló e havi kerete.")
        .contains("vendégek")
        .doesNotContain("Újra");
    assertThat(model.requests()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from assistant_draft where household_id = ?",
                Integer.class,
                annasHome))
        .isZero();
  }

  @Test
  void anUnavailableModelIsADangerAlertWithRetryAndKeepsTheText() throws Exception {
    model.setFailing(true);

    String page =
        body(
            asAnna(
                    post("/assistant/drafts")
                        .param("week", WEEK)
                        .param("request", "vendégek")
                        .param("hint", "leftovers"))
                .andExpect(status().isServiceUnavailable()));

    assertThat(page)
        .contains("data-kind=\"danger\"")
        .contains("A varázsló most nem érhető el.")
        .contains("Újra")
        .contains("vendégek")
        .contains("name=\"hint\" value=\"leftovers\" checked");
  }

  @Test
  void aSlowModelStillAnswers() throws Exception {
    model.setDelay(Duration.ofMillis(200));

    asAnna(post("/assistant/drafts").param("week", WEEK)).andExpect(status().isOk());
  }

  @Test
  void textTheModelWroteIsEscaped() throws Exception {
    model.setIdeaReason("<script>alert(1)</script> & more");

    String page = draftPage();

    assertThat(page)
        .contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; more")
        .doesNotContain("<script>alert");
  }

  @Test
  void aDraftCanBeReadAgain() throws Exception {
    long id = draftId(draftPage());

    String again = body(asAnna(get("/assistant/drafts/" + id)).andExpect(status().isOk()));

    assertThat(again).contains("id=\"assistant-draft\"").contains("Gulyás");
  }

  // ---- changing the draft ----

  @Test
  void aFollowUpChangesTheDraft() throws Exception {
    long id = draftId(draftPage());

    String refined =
        body(
            asAnna(post("/assistant/drafts/" + id + "/refine").param("message", "kevesebb hús"))
                .andExpect(status().isOk()));

    assertThat(refined)
        .contains("Gulyás")
        .doesNotContain("Tökkrémleves")
        .contains("Az ételeid közül: 3 · Új ötlet: 0");
    assertThat(model.requests()).containsExactly("vendégek", "kevesebb hús");
  }

  @Test
  void aFollowUpOverAThousandCharactersKeepsTheDraftAndSendsNothing() throws Exception {
    long id = draftId(draftPage());

    String page =
        body(
            asAnna(post("/assistant/drafts/" + id + "/refine").param("message", "c".repeat(1001)))
                .andExpect(status().isUnprocessableContent()));

    assertThat(page).contains("Legfeljebb 1000 karakter lehet.").contains("Tökkrémleves");
    assertThat(model.requests()).hasSize(1);
  }

  @Test
  void aFollowUpThatFailsKeepsTheDraftAndTheText() throws Exception {
    long id = draftId(draftPage());
    model.setFailing(true);

    String page =
        body(
            asAnna(post("/assistant/drafts/" + id + "/refine").param("message", "hal pénteken"))
                .andExpect(status().isServiceUnavailable()));

    assertThat(page)
        .contains("A varázsló most nem érhető el.")
        .contains("hal pénteken")
        .contains("Tökkrémleves");
  }

  @Test
  void aFollowUpAtTheSpendCapShowsTheWarning() throws Exception {
    long id = draftId(draftPage());
    ledger.record(annasHome, 10_000_000, 10_000_000);

    String page =
        body(
            asAnna(post("/assistant/drafts/" + id + "/refine").param("message", "hal"))
                .andExpect(status().isTooManyRequests()));

    assertThat(page).contains("Elfogyott a varázsló e havi kerete.").contains("Gulyás");
  }

  @Test
  void anItemCanBeRemoved() throws Exception {
    String page = draftPage();
    long id = draftId(page);
    long item = firstGroup(FIRST_ITEM_ID, page);

    String after =
        body(
            asAnna(post("/assistant/drafts/" + id + "/items/" + item + "/remove"))
                .andExpect(status().isOk()));

    assertThat(after).doesNotContain("/items/" + item + "/remove");
    assertThat(after).contains("Az ételeid közül: 2 · Új ötlet: 1");
  }

  @Test
  void anIdeaAddedToTheLibraryBecomesARowWithANote() throws Exception {
    String page = draftPage();
    long id = draftId(page);
    long idea = firstGroup(IDEA_ITEM_ID, page);

    String after =
        body(
            asAnna(post("/assistant/drafts/" + id + "/items/" + idea + "/idea"))
                .andExpect(status().isOk()));

    assertThat(after)
        .contains("Felvéve az ételek közé")
        .contains("tabindex=\"-1\" data-autofocus=\"true\">")
        .doesNotContain("data-idea")
        .contains("Az ételeid közül: 4 · Új ötlet: 0");
    assertThat(meals.search(anna, annasHome, new MealSearch("", null, null, false)))
        .extracting(MealView::name)
        .contains("Tökkrémleves");
  }

  // ---- accept and discard ----

  @Test
  void acceptingMergesTheMealsIntoThePlanAndTellsTheBrowserToGoThere() throws Exception {
    String page = draftPage();
    long id = draftId(page);

    var result =
        asAnna(
                post("/assistant/drafts/" + id + "/accept")
                    .header("HX-Current-URL", "http://localhost/plan"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    assertThat(result.getHeader("HX-Redirect")).isEqualTo("/plan?week=" + WEEK);
    assertThat(result.getHeader("HX-Refresh")).isNull();
    List<SlotView> slots = planning.week(anna, annasHome, MONDAY).slots();
    assertThat(slots).hasSize(3);
  }

  @Test
  void acceptingOnThePageItGoesToRefreshesIt() throws Exception {
    long id = draftId(draftPage());

    var result =
        asAnna(
                post("/assistant/drafts/" + id + "/accept")
                    .header("HX-Current-URL", "http://localhost/plan?week=" + WEEK))
            .andReturn()
            .getResponse();

    assertThat(result.getHeader("HX-Refresh")).isEqualTo("true");
  }

  @Test
  void aSecondAcceptIsAConflictAndWritesNothing() throws Exception {
    long id = draftId(draftPage());
    asAnna(post("/assistant/drafts/" + id + "/accept")).andExpect(status().isOk());

    var second =
        asAnna(post("/assistant/drafts/" + id + "/accept"))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse();

    assertThat(second.getHeader("HX-Retarget")).isEqualTo("#plan-conflict");
    assertThat(second.getContentAsString()).contains("/plan?week=" + WEEK);
    assertThat(planning.week(anna, annasHome, MONDAY).slots()).hasSize(3);
  }

  @Test
  void aClosedDraftCannotBeChangedAnyMore() throws Exception {
    String page = draftPage();
    long id = draftId(page);
    long item = firstGroup(FIRST_ITEM_ID, page);
    long idea = firstGroup(IDEA_ITEM_ID, page);
    asAnna(post("/assistant/drafts/" + id + "/accept")).andExpect(status().isOk());

    asAnna(post("/assistant/drafts/" + id + "/refine").param("message", "x"))
        .andExpect(status().isConflict());
    asAnna(post("/assistant/drafts/" + id + "/items/" + item + "/remove"))
        .andExpect(status().isConflict());
    asAnna(post("/assistant/drafts/" + id + "/items/" + idea + "/idea"))
        .andExpect(status().isConflict());
  }

  @Test
  void acceptingTellsThePlanPageOnceHowItWent() throws Exception {
    long id = draftId(draftPage());
    Cookie session =
        mvc.perform(get("/plan").with(signedInAs(anna)))
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    assertThat(session).isNotNull();

    mvc.perform(
        post("/assistant/drafts/" + id + "/accept")
            .with(signedInAs(anna))
            .with(csrf())
            .cookie(session));
    String notice =
        body(
            mvc.perform(get("/assistant/notice").with(signedInAs(anna)).cookie(session))
                .andExpect(status().isOk()));
    var again =
        mvc.perform(get("/assistant/notice").with(signedInAs(anna)).cookie(session))
            .andReturn()
            .getResponse();

    assertThat(notice)
        .contains("data-kind=\"success\"")
        .contains("Tádám! Bekerült a hetedbe: 3 étel. Kimaradt ötlet: 1.");
    assertThat(again.getStatus()).isEqualTo(204);
    assertThat(again.getContentAsString()).isEmpty();
  }

  @Test
  void replacingTheWeekClearsWhatWasPlanned() throws Exception {
    long other = meals.create(anna, annasHome, new MealDetails("Régi", null, null, List.of())).id();
    planning.add(anna, annasHome, MONDAY.plusDays(2), SlotType.BREAKFAST, other, null);
    long id = draftId(draftPage());

    asAnna(post("/assistant/drafts/" + id + "/accept").param("mode", "REPLACE"))
        .andExpect(status().isOk());

    assertThat(planning.week(anna, annasHome, MONDAY).slots())
        .extracting(SlotView::slotType)
        .doesNotContain(SlotType.BREAKFAST)
        .hasSize(4);
  }

  @Test
  void anUnknownAcceptModeIsABadRequest() throws Exception {
    long id = draftId(draftPage());

    asAnna(post("/assistant/drafts/" + id + "/accept").param("mode", "bogus"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aDraftWithNoLibraryMealCannotBeAccepted() throws Exception {
    String page = draftPage();
    long id = draftId(page);
    // Remove every library item; only the idea is left.
    Matcher items = FIRST_ITEM_ID.matcher(page);
    while (items.find()) {
      long item = Long.parseLong(items.group(1));
      if (item != firstGroup(IDEA_ITEM_ID, page)) {
        asAnna(post("/assistant/drafts/" + id + "/items/" + item + "/remove"))
            .andExpect(status().isOk());
      }
    }

    String after = body(asAnna(get("/assistant/drafts/" + id)).andExpect(status().isOk()));
    String refused =
        body(
            asAnna(post("/assistant/drafts/" + id + "/accept"))
                .andExpect(status().isUnprocessableContent()));

    assertThat(after).contains("id=\"assistant-accept\"").contains("disabled");
    assertThat(refused)
        .contains("Ez a javaslat most nem kerülhet a tervbe.")
        .contains("Tökkrémleves");
  }

  @Test
  void discardingDeletesTheDraftAndAsksTheSheetToClose() throws Exception {
    long id = draftId(draftPage());

    var result =
        asAnna(post("/assistant/drafts/" + id + "/discard"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    assertThat(result.getHeader("HX-Trigger")).isEqualTo("assistant-close");
    asAnna(get("/assistant/drafts/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void aStrangersDraftIsNotFoundOnEveryRoute() throws Exception {
    String page = draftPage();
    long id = draftId(page);
    long item = firstGroup(FIRST_ITEM_ID, page);
    // Bela has his own household, so he reaches the routes but not Anna's draft.
    as(bela, get("/assistant/drafts/" + id)).andExpect(status().isNotFound());
    as(bela, post("/assistant/drafts/" + id + "/refine").param("message", "x"))
        .andExpect(status().isNotFound());
    as(bela, post("/assistant/drafts/" + id + "/items/" + item + "/remove"))
        .andExpect(status().isNotFound());
    as(bela, post("/assistant/drafts/" + id + "/items/" + item + "/idea"))
        .andExpect(status().isNotFound());
    as(bela, post("/assistant/drafts/" + id + "/accept")).andExpect(status().isNotFound());
    as(bela, post("/assistant/drafts/" + id + "/discard")).andExpect(status().isNotFound());
    assertThat(model.requests()).hasSize(1);
    assertThat(planning.week(anna, annasHome, MONDAY).slots()).isEmpty();
    assertThat(belasHome).isNotEqualTo(annasHome);
  }

  // ---- one slot ----

  @Test
  void thePickSheetsRowAsksTheWizard() throws Exception {
    String row =
        body(
            asAnna(get("/assistant/slot").param("date", "2020-05-12").param("slotType", "DINNER"))
                .andExpect(status().isOk()));

    assertThat(row)
        .contains("hx-post=\"/assistant/slot\"")
        .contains("name=\"date\" value=\"2020-05-12\"")
        .contains("name=\"slotType\" value=\"DINNER\"")
        .contains("Ötletet kérek a varázslótól")
        .contains("data-icon=\"wand\"");
  }

  @Test
  void upToThreeSuggestionsComeWithThePlanThisForm() throws Exception {
    meal("Negyedik");

    String page =
        body(
            asAnna(post("/assistant/slot").param("date", "2020-05-12").param("slotType", "DINNER"))
                .andExpect(status().isOk()));

    assertThat(page)
        .contains("A varázsló ötletei")
        .contains("Jól illik ide.")
        .contains("action=\"/plan/slots\"")
        .contains("name=\"date\" value=\"2020-05-12\"")
        .contains("name=\"slot\" value=\"DINNER\"");
    assertThat(page.split("name=\"mealId\"", -1)).hasSize(4);
  }

  @Test
  void aHouseholdWithNoMealsGetsTheNoIdeasLine() throws Exception {
    String page =
        body(
            as(bela, post("/assistant/slot").param("date", "2020-05-12").param("slotType", "LUNCH"))
                .andExpect(status().isOk()));

    assertThat(page).contains("Most nincs jó ötletem erre.").doesNotContain("name=\"mealId\"");
  }

  @Test
  void aSlotRequestThatFailsKeepsTheAskRowAsTheRetry() throws Exception {
    model.setFailing(true);

    String unavailable =
        body(
            asAnna(post("/assistant/slot").param("date", "2020-05-12").param("slotType", "DINNER"))
                .andExpect(status().isServiceUnavailable()));
    ledger.record(annasHome, 10_000_000, 10_000_000);
    model.setFailing(false);
    String capped =
        body(
            asAnna(post("/assistant/slot").param("date", "2020-05-12").param("slotType", "DINNER"))
                .andExpect(status().isTooManyRequests()));

    assertThat(unavailable)
        .contains("A varázsló most nem érhető el.")
        .contains("Újra")
        .contains("Ötletet kérek a varázslótól");
    assertThat(capped)
        .contains("Elfogyott a varázsló e havi kerete.")
        .contains("Ötletet kérek a varázslótól");
  }

  @Test
  void badSlotParametersAreRefused() throws Exception {
    asAnna(get("/assistant/slot").param("date", "tomorrow").param("slotType", "DINNER"))
        .andExpect(status().isBadRequest());
    asAnna(get("/assistant/slot").param("date", "2020-05-12").param("slotType", "BRUNCH"))
        .andExpect(status().isBadRequest());
    asAnna(post("/assistant/slot").param("slotType", "DINNER")).andExpect(status().isBadRequest());
    asAnna(post("/assistant/slot").param("date", "2020-05-12")).andExpect(status().isBadRequest());
  }

  // ---- helpers ----

  private static java.util.Properties bundle(String name) {
    var properties = new java.util.Properties();
    try (var in =
        new java.io.InputStreamReader(
            AssistantPagesTest.class.getClassLoader().getResourceAsStream(name),
            java.nio.charset.StandardCharsets.UTF_8)) {
      properties.load(in);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    return properties;
  }
}
