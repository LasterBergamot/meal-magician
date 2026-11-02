package hu.lasterbergamot.mealmagician.planning;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The reuse routes through the whole stack: the reuse sheet, copying a day or a week, saving and
 * applying templates, the occasion strip, and their 400, 404, 409 and 422 answers. Anna owns
 * "Annas"; Bela owns "Belas" and must never reach Anna's templates. The week is a fixed one far
 * from today (Monday 2020-05-11, week 20). Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PlanReusePagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2020, 5, 11);
  private static final LocalDate TUESDAY = LocalDate.of(2020, 5, 12);

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private PlanTemplateService templates;
  @Autowired private OccasionService occasions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private java.time.Clock clock;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
  }

  private ResultActions asAnna(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(anna)).with(csrf()));
  }

  private ResultActions htmx(MockHttpServletRequestBuilder request) throws Exception {
    return asAnna(request.header("HX-Request", "true"));
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private MealView meal(String name) {
    return meals.create(anna, annasHome, new MealDetails(name, null, null, List.of()));
  }

  private SlotView plan(LocalDate date, long mealId) {
    return planning.add(anna, annasHome, date, SlotType.LUNCH, mealId, null);
  }

  private List<SlotView> slotsOn(LocalDate date) {
    return planning.week(anna, annasHome, date).slots().stream()
        .filter(slot -> slot.date().equals(date))
        .toList();
  }

  private static int count(String text, String part) {
    return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
  }

  // ---- the week page ----

  @Test
  void theWeekPageOffersTheReuseSheetItsOpenersAndTheTemplatesLink() throws Exception {
    String page = body(asAnna(get("/plan").param("week", "2020-W20")).andExpect(status().isOk()));

    assertThat(page)
        .contains("id=\"reuse-sheet\"")
        .contains("id=\"plan-reuse-week\"")
        .contains("data-reuse=\"2020-05-11\"")
        .contains("href=\"/plan/templates\"");
    // Seven days on the phone's list and in the table.
    assertThat(count(page, "data-reuse=\"")).isEqualTo(14);
  }

  @Test
  void theWeeksOccasionsAreLinkedAboveTheDays() throws Exception {
    OccasionView inside =
        occasions.create(
            anna,
            annasHome,
            new OccasionDetails(
                "Szülinap", LocalDate.of(2020, 5, 13), OccasionType.BIRTHDAY, null));
    OccasionView outside =
        occasions.create(
            anna,
            annasHome,
            new OccasionDetails("Jövő hét", LocalDate.of(2020, 5, 18), OccasionType.PARTY, null));

    String page = body(asAnna(get("/plan").param("week", "2020-W20")).andExpect(status().isOk()));

    assertThat(page)
        .contains("data-occasion-id=\"" + inside.id() + "\"")
        .contains("href=\"/occasions/" + inside.id() + "\"")
        .doesNotContain("data-occasion-id=\"" + outside.id() + "\"");
  }

  @Test
  void aWeekWithoutOccasionsDrawsNoStrip() throws Exception {
    asAnna(get("/plan").param("week", "2020-W20"))
        .andExpect(content().string(not(containsString("data-occasion-id"))));
  }

  // ---- the reuse sheet ----

  @Test
  void theDaySheetOffersCopySaveAndApplyForAPlannedDay() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    String sheet =
        body(
            htmx(get("/plan/reuse/day").param("date", "2020-05-11"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html")))));

    assertThat(sheet)
        .contains("id=\"copy-form\"")
        .contains("value=\"2020-05-18\"")
        .contains("id=\"copy-replace\"")
        .contains("id=\"save-template-form\"")
        .contains("Tervezett ételek: 1")
        .contains("Még nincs napsablon");
  }

  @Test
  void anEmptyDaySaysSoAndOffersNoCopy() throws Exception {
    String sheet = body(htmx(get("/plan/reuse/day").param("date", "2020-05-11")));

    assertThat(sheet)
        .contains("Erre a napra még nincs étel tervezve")
        .doesNotContain("id=\"copy-form\"")
        .doesNotContain("id=\"save-template-form\"");
  }

  @Test
  void theDaySheetListsOnlyDayTemplatesWithTheirApplyActions() throws Exception {
    plan(MONDAY, meal("Lecsó").id());
    PlanTemplateView day = templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Napom");
    templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "Hetem");

    String sheet = body(htmx(get("/plan/reuse/day").param("date", "2020-05-11")));

    assertThat(sheet)
        .contains("data-template-id=\"" + day.id() + "\"")
        .contains("Napom")
        .doesNotContain("Hetem")
        .contains("/plan/templates/" + day.id() + "/apply?mode=MERGE")
        .contains("id=\"apply-replace-" + day.id() + "\"")
        .contains("/plan/templates/" + day.id() + "/apply?mode=REPLACE&amp;date=2020-05-11");
  }

  @Test
  void theWeekSheetOffersTwelveFutureWeeks() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    String sheet = body(htmx(get("/plan/reuse/week").param("week", "2020-W20")));

    assertThat(sheet)
        .contains("id=\"copy-to\"")
        .contains("/plan/weeks/copy?mode=MERGE")
        .contains("value=\"2020-W20\"");
    assertThat(count(sheet, "<option")).isEqualTo(12);
  }

  @Test
  void theWeekSheetOmitsTheShownWeekFromItsTargetsWhenItIsInTheFuture() throws Exception {
    LocalDate thisMonday =
        LocalDate.now(clock)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    plan(thisMonday, meal("Lecsó").id());
    String code =
        java.time.format.DateTimeFormatter.ISO_WEEK_DATE.format(thisMonday).substring(0, 8);

    String sheet = body(htmx(get("/plan/reuse/week").param("week", code)));

    assertThat(count(sheet, "<option")).isEqualTo(11);
    assertThat(sheet).doesNotContain("<option value=\"" + code + "\"");
  }

  @Test
  void withoutHtmxTheSheetIsAWholePageWithABackLink() throws Exception {
    asAnna(get("/plan/reuse/day").param("date", "2020-05-11"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Vissza a héthez")));
  }

  @Test
  void aMalformedSheetRequestIsBad() throws Exception {
    asAnna(get("/plan/reuse/day")).andExpect(status().isBadRequest());
    asAnna(get("/plan/reuse/day").param("date", "x")).andExpect(status().isBadRequest());
    asAnna(get("/plan/reuse/week").param("week", "nope")).andExpect(status().isBadRequest());
  }

  // ---- copying a day ----

  @Test
  void mergingADayCopiesItAndLandsOnTheTarget() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());

    htmx(post("/plan/days/copy")
            .param("mode", "MERGE")
            .param("from", "2020-05-11")
            .param("to", "2020-05-12"))
        .andExpect(header().string("HX-Redirect", "/plan?week=2020-W20#day-2020-05-12"));

    assertThat(slotsOn(TUESDAY)).extracting(SlotView::mealId).containsExactly(stew.id());
  }

  @Test
  void replacingADayLeavesOnlyTheCopy() throws Exception {
    MealView stew = meal("Lecsó");
    MealView soup = meal("Leves");
    plan(MONDAY, stew.id());
    plan(TUESDAY, soup.id());

    htmx(post("/plan/days/copy")
            .param("mode", "REPLACE")
            .param("from", "2020-05-11")
            .param("to", "2020-05-12"))
        .andExpect(header().string("HX-Redirect", "/plan?week=2020-W20#day-2020-05-12"));

    assertThat(slotsOn(TUESDAY)).extracting(SlotView::mealId).containsExactly(stew.id());
  }

  @Test
  void copyingADayOntoItselfIsRefusedIntoTheBanner() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    htmx(post("/plan/days/copy")
            .param("mode", "MERGE")
            .param("from", "2020-05-11")
            .param("to", "2020-05-11"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"))
        .andExpect(header().string("HX-Reswap", "innerHTML"))
        .andExpect(content().string(containsString("Másik napot vagy hetet")))
        .andExpect(content().string(containsString("href=\"/plan?week=2020-W20\"")));
  }

  @Test
  void aRefusedCopyWithoutHtmxIsTheWholeWeekPageWithTheMessage() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    asAnna(
            post("/plan/days/copy")
                .param("mode", "MERGE")
                .param("from", "2020-05-11")
                .param("to", "2020-05-11"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("id=\"reuse-sheet\"")))
        .andExpect(content().string(containsString("Másik napot vagy hetet")));
  }

  @Test
  void copyingAnEmptyDayIsRefused() throws Exception {
    htmx(post("/plan/days/copy")
            .param("mode", "MERGE")
            .param("from", "2020-05-11")
            .param("to", "2020-05-12"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Itt nincs mit másolni")));
  }

  @Test
  void aDayCopyWithMissingParametersIsBad() throws Exception {
    htmx(post("/plan/days/copy").param("from", "2020-05-11").param("to", "2020-05-12"))
        .andExpect(status().isBadRequest());
    htmx(post("/plan/days/copy").param("mode", "MERGE").param("from", "2020-05-11"))
        .andExpect(status().isBadRequest());
  }

  // ---- copying a week ----

  @Test
  void mergingAWeekCopiesItAndLandsOnTheTargetsMonday() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());

    htmx(post("/plan/weeks/copy")
            .param("mode", "MERGE")
            .param("from", "2020-W20")
            .param("to", "2020-W21"))
        .andExpect(header().string("HX-Redirect", "/plan?week=2020-W21#day-2020-05-18"));

    assertThat(slotsOn(MONDAY.plusWeeks(1)))
        .extracting(SlotView::mealId)
        .containsExactly(stew.id());
  }

  @Test
  void copyingAWeekOntoItselfIsRefused() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    htmx(post("/plan/weeks/copy")
            .param("mode", "MERGE")
            .param("from", "2020-W20")
            .param("to", "2020-W20"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"))
        .andExpect(content().string(containsString("Másik napot vagy hetet")));
  }

  @Test
  void aDayReplaceThatLosesTheRaceIsAConflict() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());
    SlotView target = plan(TUESDAY, stew.id());
    jdbc.update("update meal_slot set version = version + 1 where id = ?", target.id());

    htmx(post("/plan/days/copy")
            .param("mode", "REPLACE")
            .param("from", "2020-05-11")
            .param("to", "2020-05-12"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"));
  }

  @Test
  void aReplaceThatLosesTheRaceIsAConflict() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());
    SlotView target = plan(MONDAY.plusWeeks(1), stew.id());
    jdbc.update("update meal_slot set version = version + 1 where id = ?", target.id());

    htmx(post("/plan/weeks/copy")
            .param("mode", "REPLACE")
            .param("from", "2020-W20")
            .param("to", "2020-W21"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"));
  }

  @Test
  void aWeekCopyWithAMalformedWeekIsBad() throws Exception {
    htmx(post("/plan/weeks/copy")
            .param("mode", "MERGE")
            .param("from", "2020-W20")
            .param("to", "nope"))
        .andExpect(status().isBadRequest());
  }

  // ---- saving a template ----

  @Test
  void savingADayAnswersWithTheSuccessAlert() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    String saved =
        body(
            htmx(post("/plan/templates")
                    .param("scope", "DAY")
                    .param("date", "2020-05-11")
                    .param("name", "Napom"))
                .andExpect(status().isOk()));

    assertThat(saved).contains("Sablon elmentve").contains("id=\"save-template-form\"");
    assertThat(templates.list(anna, annasHome))
        .extracting(PlanTemplateSummary::name)
        .contains("Napom");
  }

  @Test
  void savingWithoutHtmxRedirectsToTheTemplatesPage() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    asAnna(
            post("/plan/templates")
                .param("scope", "WEEK")
                .param("date", "2020-05-11")
                .param("name", "Hetem"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/plan/templates"));
  }

  @Test
  void aBlankNameKeepsTheFormAndShowsTheMessage() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    htmx(post("/plan/templates")
            .param("scope", "DAY")
            .param("date", "2020-05-11")
            .param("name", "  "))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"save-template-form\"")))
        .andExpect(content().string(containsString("Adj nevet a sablonnak.")));
  }

  @Test
  void aTooLongNameOfAWeekKeepsTheTypedText() throws Exception {
    plan(MONDAY, meal("Lecsó").id());
    String name = "x".repeat(101);

    htmx(post("/plan/templates")
            .param("scope", "WEEK")
            .param("date", "2020-05-11")
            .param("name", name))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("legfeljebb 100")))
        .andExpect(content().string(containsString("value=\"" + name + "\"")))
        .andExpect(content().string(containsString("value=\"WEEK\"")));
  }

  @Test
  void savingAnEmptyDayIsRefused() throws Exception {
    htmx(post("/plan/templates")
            .param("scope", "DAY")
            .param("date", "2020-05-11")
            .param("name", "Üres"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Itt nincs mit másolni")));
  }

  @Test
  void aBlankNameWithoutHtmxIsTheWholeReusePage() throws Exception {
    plan(MONDAY, meal("Lecsó").id());

    asAnna(
            post("/plan/templates")
                .param("scope", "DAY")
                .param("date", "2020-05-11")
                .param("name", ""))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Adj nevet a sablonnak.")));
  }

  @Test
  void savingWithAMalformedScopeOrDateIsBad() throws Exception {
    htmx(post("/plan/templates").param("scope", "MONTH").param("date", "2020-05-11"))
        .andExpect(status().isBadRequest());
    htmx(post("/plan/templates").param("scope", "DAY").param("date", "x"))
        .andExpect(status().isBadRequest());
  }

  // ---- applying a template ----

  @Test
  void applyingADayTemplateFillsTheChosenDay() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());
    PlanTemplateView day = templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Napom");

    htmx(post("/plan/templates/" + day.id() + "/apply")
            .param("mode", "MERGE")
            .param("date", "2020-05-14"))
        .andExpect(header().string("HX-Redirect", "/plan?week=2020-W20#day-2020-05-14"));

    assertThat(slotsOn(LocalDate.of(2020, 5, 14)))
        .extracting(SlotView::mealId)
        .containsExactly(stew.id());
  }

  @Test
  void applyingAWeekTemplateLandsOnTheTargetWeeksMonday() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());
    PlanTemplateView week = templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "Hetem");

    htmx(post("/plan/templates/" + week.id() + "/apply")
            .param("mode", "MERGE")
            .param("date", "2020-05-20"))
        .andExpect(header().string("HX-Redirect", "/plan?week=2020-W21#day-2020-05-18"));

    assertThat(slotsOn(MONDAY.plusWeeks(1)))
        .extracting(SlotView::mealId)
        .containsExactly(stew.id());
  }

  @Test
  void aReplaceTemplateThatLosesTheRaceIsAConflict() throws Exception {
    MealView stew = meal("Lecsó");
    plan(MONDAY, stew.id());
    PlanTemplateView day = templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Napom");
    SlotView target = plan(TUESDAY, stew.id());
    jdbc.update("update meal_slot set version = version + 1 where id = ?", target.id());

    htmx(post("/plan/templates/" + day.id() + "/apply")
            .param("mode", "REPLACE")
            .param("date", "2020-05-12"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"));
  }

  @Test
  void anotherHouseholdsTemplateIs404BeforeAnyParameterIsRead() throws Exception {
    long belasMeal =
        meals.create(bela, belasHome, new MealDetails("Belas étel", null, null, List.of())).id();
    planning.add(bela, belasHome, MONDAY, SlotType.LUNCH, belasMeal, null);
    PlanTemplateView theirs = templates.save(bela, belasHome, TemplateScope.DAY, MONDAY, "Belas");

    htmx(post("/plan/templates/" + theirs.id() + "/apply")
            .param("mode", "MERGE")
            .param("date", "2020-05-14"))
        .andExpect(status().isNotFound());
    htmx(post("/plan/templates/" + theirs.id() + "/apply")).andExpect(status().isNotFound());
  }

  @Test
  void applyingWithoutAModeIsBad() throws Exception {
    plan(MONDAY, meal("Lecsó").id());
    PlanTemplateView day = templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Napom");

    htmx(post("/plan/templates/" + day.id() + "/apply").param("date", "2020-05-14"))
        .andExpect(status().isBadRequest());
  }
}
