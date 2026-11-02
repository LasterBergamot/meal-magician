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
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
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
 * The week editing routes through the whole stack: the pick sheet, quick add, the slot editor and
 * the note, move, reorder and remove actions, with their 400, 404, 409 and 422 answers. Anna owns
 * "Annas"; Bela owns "Belas" and must never reach Anna's data (PLAN.md D8). The week is a fixed one
 * far from today (Monday 2020-05-11, week 20). Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PlanEditingPagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2020, 5, 11);
  private static final String REDIRECT_TO_MONDAY = "/plan?week=2020-W20#day-2020-05-11";
  private static final String REDIRECT_TO_WEDNESDAY = "/plan?week=2020-W20#day-2020-05-13";

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private JdbcTemplate jdbc;

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

  private MealView annasMeal(String name) {
    return meals.create(anna, annasHome, new MealDetails(name, null, null, List.of()));
  }

  private SlotView plan(LocalDate date, SlotType type, long mealId) {
    return planning.add(anna, annasHome, date, type, mealId, null);
  }

  private SlotView current(long slotId) {
    return planning.slot(anna, annasHome, slotId);
  }

  private static int count(String text, String part) {
    return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  // ---- the week page ----

  @Test
  void theWeekPageOffersAnAddControlPerSlotAndBothSheets() throws Exception {
    String page = body(asAnna(get("/plan").param("week", "2020-W20")).andExpect(status().isOk()));

    assertThat(page)
        .contains("data-add=\"2020-05-11/LUNCH\"")
        .contains("href=\"/plan/pick?date=2020-05-11&amp;slot=LUNCH\"")
        .contains("id=\"pick-sheet\"")
        .contains("id=\"slot-sheet\"")
        .contains("id=\"plan-conflict\"")
        .doesNotContain("Még nincs terv");
    // Seven days, four slot types, on the phone's list and in the table.
    assertThat(count(page, "data-add=\"")).isEqualTo(56);
  }

  // ---- the pick sheet ----

  @Test
  void thePickSheetListsThePlannedMealFirstThenByName() throws Exception {
    annasMeal("Bableves");
    annasMeal("alma");
    MealView planned = annasMeal("Zserbó");
    plan(MONDAY, SlotType.LUNCH, planned.id());

    String sheet =
        body(
            htmx(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Vary", containsString("HX-Request")))
                .andExpect(content().string(not(containsString("<html")))));

    assertThat(sheet.indexOf("Zserbó")).isPositive().isLessThan(sheet.indexOf("alma"));
    assertThat(sheet.indexOf("alma")).isLessThan(sheet.indexOf("Bableves"));
    assertThat(sheet).contains("name=\"date\" value=\"2020-05-13\"").contains("Szerda");
  }

  @Test
  void archivedAndOtherHouseholdsMealsAreNotOffered() throws Exception {
    MealView old = annasMeal("Régi rántotta");
    meals.archive(anna, annasHome, old.id());
    meals.quickAdd(bela, belasHome, "Belas titok");
    annasMeal("Friss saláta");

    htmx(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER"))
        .andExpect(content().string(containsString("Friss saláta")))
        .andExpect(content().string(not(containsString("Régi rántotta"))))
        .andExpect(content().string(not(containsString("Belas titok"))));
  }

  @Test
  void theSearchFiltersTheLibrary() throws Exception {
    annasMeal("Gulyásleves");
    annasMeal("Palacsinta");

    htmx(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER").param("q", "leves"))
        .andExpect(content().string(containsString("Gulyásleves")))
        .andExpect(content().string(not(containsString("Palacsinta"))));
  }

  @Test
  void aLongLibraryShowsTwentyMealsAndSaysSo() throws Exception {
    for (int i = 1; i <= 21; i++) {
      meals.quickAdd(anna, annasHome, "Étel %02d".formatted(i));
    }

    String sheet =
        body(htmx(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER")));

    assertThat(count(sheet, "name=\"mealId\"")).isEqualTo(20);
    assertThat(sheet).contains("Az első 20 étel látszik");
  }

  @Test
  void anEmptyLibrarySaysSo() throws Exception {
    htmx(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER"))
        .andExpect(content().string(containsString("Még üres az ételkönyvtárad")))
        .andExpect(content().string(containsString("id=\"pick-quick-form\"")));
  }

  @Test
  void aSearchWithoutAMatchSaysSo() throws Exception {
    annasMeal("Palacsinta");

    htmx(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER").param("q", "zzz"))
        .andExpect(content().string(containsString("Nincs ilyen étel")))
        .andExpect(content().string(not(containsString("Még üres az ételkönyvtárad"))));
  }

  @Test
  void withoutHtmxThePickPageIsAWholePageWithABackLink() throws Exception {
    asAnna(get("/plan/pick").param("date", "2020-05-13").param("slot", "DINNER"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Vissza a héthez")))
        .andExpect(content().string(containsString("href=\"/plan?week=2020-W20\"")));
  }

  @Test
  void aMalformedPickRequestIsBad() throws Exception {
    asAnna(get("/plan/pick").param("date", "x").param("slot", "DINNER"))
        .andExpect(status().isBadRequest());
    asAnna(get("/plan/pick").param("date", "2020-05-13").param("slot", "FOO"))
        .andExpect(status().isBadRequest());
    asAnna(get("/plan/pick")).andExpect(status().isBadRequest());
  }

  // ---- add ----

  @Test
  void addingALibraryMealPlansItAndRedirectsToThatDay() throws Exception {
    MealView soup = annasMeal("Gulyásleves");

    htmx(post("/plan/slots")
            .param("date", "2020-05-11")
            .param("slot", "LUNCH")
            .param("mealId", String.valueOf(soup.id())))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY));

    assertThat(planning.week(anna, annasHome, MONDAY).slots())
        .singleElement()
        .satisfies(slot -> assertThat(slot.mealId()).isEqualTo(soup.id()));
  }

  @Test
  void addingWithoutHtmxRedirectsWithSeeOther() throws Exception {
    MealView soup = annasMeal("Gulyásleves");

    asAnna(
            post("/plan/slots")
                .param("date", "2020-05-11")
                .param("slot", "LUNCH")
                .param("mealId", String.valueOf(soup.id())))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", REDIRECT_TO_MONDAY));
  }

  @Test
  void anEditMadeOnTheVeryWeekPageAlsoAsksHtmxToReload() throws Exception {
    MealView soup = annasMeal("Gulyásleves");

    // A browser would only jump to #day-... without loading the page, so the edit would not show.
    for (String here :
        List.of(
            "http://localhost:8080/plan?week=2020-W20",
            "http://localhost:8080/plan?week=2020-W20#day-2020-05-13")) {
      htmx(post("/plan/slots")
              .header("HX-Current-URL", here)
              .param("date", "2020-05-11")
              .param("slot", "LUNCH")
              .param("mealId", String.valueOf(soup.id())))
          .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY))
          .andExpect(header().string("HX-Refresh", "true"));
    }
  }

  @Test
  void anEditMadeFromAnotherPageJustNavigates() throws Exception {
    MealView soup = annasMeal("Gulyásleves");

    for (String here :
        List.of(
            "http://localhost:8080/plan",
            "http://localhost:8080/plan?week=2020-W21",
            "http://localhost:8080/plan/slots/3",
            "http://[not an address")) {
      htmx(post("/plan/slots")
              .header("HX-Current-URL", here)
              .param("date", "2020-05-11")
              .param("slot", "LUNCH")
              .param("mealId", String.valueOf(soup.id())))
          .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY))
          .andExpect(header().doesNotExist("HX-Refresh"));
    }
  }

  @Test
  void addingAnotherHouseholdsMealIsNotFoundAndAMissingMealIdIsBad() throws Exception {
    long belasMeal = meals.quickAdd(bela, belasHome, "Belas titok").id();

    asAnna(
            post("/plan/slots")
                .param("date", "2020-05-11")
                .param("slot", "LUNCH")
                .param("mealId", String.valueOf(belasMeal)))
        .andExpect(status().isNotFound());
    asAnna(post("/plan/slots").param("date", "2020-05-11").param("slot", "LUNCH"))
        .andExpect(status().isBadRequest());
    assertThat(planning.week(anna, annasHome, MONDAY).slots()).isEmpty();
  }

  // ---- quick add ----

  @Test
  void quickAddCreatesTheMealAndThePlanEntry() throws Exception {
    htmx(post("/plan/slots/quick")
            .param("date", "2020-05-11")
            .param("slot", "DINNER")
            .param("name", "Lecsó"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY));

    assertThat(meals.search(anna, annasHome, new MealSearch("Lecsó", null, false))).hasSize(1);
    assertThat(planning.week(anna, annasHome, MONDAY).slots())
        .singleElement()
        .satisfies(slot -> assertThat(slot.slotType()).isEqualTo(SlotType.DINNER));
  }

  @Test
  void aBlankQuickNameIsRejectedWithTheFormAndNothingIsWritten() throws Exception {
    String form =
        body(
            htmx(post("/plan/slots/quick")
                    .param("date", "2020-05-11")
                    .param("slot", "DINNER")
                    .param("name", "  "))
                .andExpect(status().isUnprocessableContent()));

    assertThat(form)
        .contains("id=\"pick-quick-form\"")
        .contains("Adj nevet az ételnek.")
        .doesNotContain("<html");
    assertThat(planning.week(anna, annasHome, MONDAY).slots()).isEmpty();
  }

  @Test
  void aTooLongQuickNameKeepsTheTypedText() throws Exception {
    String name = "x".repeat(201);

    htmx(post("/plan/slots/quick")
            .param("date", "2020-05-11")
            .param("slot", "DINNER")
            .param("name", name))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("legfeljebb 200")))
        .andExpect(content().string(containsString(name)));
  }

  @Test
  void aBlankQuickNameWithoutHtmxShowsTheWholePickPage() throws Exception {
    asAnna(
            post("/plan/slots/quick")
                .param("date", "2020-05-11")
                .param("slot", "DINNER")
                .param("name", ""))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Adj nevet az ételnek.")))
        .andExpect(content().string(containsString("Vissza a héthez")));
  }

  @Test
  void aMalformedQuickRequestIsBad() throws Exception {
    asAnna(post("/plan/slots/quick").param("date", "nope").param("slot", "DINNER"))
        .andExpect(status().isBadRequest());
  }

  // ---- the editor ----

  @Test
  void theEditorShowsTheNoteFormSevenDaysAndOnlyTheMovesThatMakeSense() throws Exception {
    SlotView first = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());
    SlotView second = plan(MONDAY, SlotType.LUNCH, annasMeal("Pogácsa").id());
    // Same day, other type, and same type, other day: neither counts towards the group size.
    plan(MONDAY, SlotType.DINNER, annasMeal("Tészta").id());
    plan(MONDAY.plusDays(1), SlotType.LUNCH, annasMeal("Rizs").id());

    String lower = body(htmx(get("/plan/slots/" + second.id())).andExpect(status().isOk()));
    String upper = body(htmx(get("/plan/slots/" + first.id())).andExpect(status().isOk()));

    assertThat(lower)
        .contains("id=\"note-form\"")
        .contains("Pogácsa")
        .contains("id=\"reorder-up-form\"")
        .doesNotContain("id=\"reorder-down-form\"")
        .contains("id=\"remove-slot\"")
        .contains("hx-post=\"/plan/slots/" + second.id() + "/remove?version=")
        .doesNotContain("<html");
    assertThat(count(lower, "<option value=\"2020-05-")).isEqualTo(7);
    assertThat(count(lower, "<option value=\"")).isEqualTo(11);
    assertThat(upper).contains("id=\"reorder-down-form\"").doesNotContain("id=\"reorder-up-form\"");
  }

  @Test
  void theEditorShowsTheSlotsCurrentNoteInTheField() throws Exception {
    SlotView slot =
        planning.add(
            anna, annasHome, MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id(), "Dupla adag");

    htmx(get("/plan/slots/" + slot.id()))
        .andExpect(content().string(containsString("value=\"Dupla adag\"")));
  }

  @Test
  void aSingleMealHasNoOrderButtonsAndWithoutHtmxTheEditorIsAPage() throws Exception {
    SlotView only = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    asAnna(get("/plan/slots/" + only.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Vissza a héthez")))
        .andExpect(content().string(not(containsString("reorder-up-form"))))
        .andExpect(content().string(not(containsString("reorder-down-form"))));
  }

  @Test
  void anotherHouseholdsSlotIsNotFoundOnEveryRoute() throws Exception {
    long belasMeal = meals.quickAdd(bela, belasHome, "Belas titok").id();
    long belasSlot = planning.add(bela, belasHome, MONDAY, SlotType.LUNCH, belasMeal, null).id();
    String base = "/plan/slots/" + belasSlot;

    asAnna(get(base)).andExpect(status().isNotFound());
    // No parameters at all: the foreign slot is 404 before any 400.
    asAnna(post(base + "/note")).andExpect(status().isNotFound());
    asAnna(post(base + "/move")).andExpect(status().isNotFound());
    asAnna(post(base + "/reorder")).andExpect(status().isNotFound());
    asAnna(post(base + "/remove")).andExpect(status().isNotFound());
    assertThat(planning.slot(bela, belasHome, belasSlot).note()).isNull();
  }

  // ---- note ----

  @Test
  void aNoteIsSavedAndEmptyClearsIt() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/note")
            .param("version", String.valueOf(slot.version()))
            .param("note", "Dupla adag"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY));
    SlotView noted = current(slot.id());
    assertThat(noted.note()).isEqualTo("Dupla adag");

    htmx(post("/plan/slots/" + slot.id() + "/note")
            .param("version", String.valueOf(noted.version()))
            .param("note", ""))
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY));
    assertThat(current(slot.id()).note()).isNull();
  }

  @Test
  void aTooLongNoteShowsTheFormAgainWithTheTypedText() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());
    String note = "y".repeat(501);

    String form =
        body(
            htmx(post("/plan/slots/" + slot.id() + "/note")
                    .param("version", String.valueOf(slot.version()))
                    .param("note", note))
                .andExpect(status().isUnprocessableContent()));

    assertThat(form)
        .contains("id=\"note-form\"")
        .contains("legfeljebb 500")
        .contains(note)
        .doesNotContain("<html");
    assertThat(current(slot.id()).note()).isNull();
  }

  @Test
  void aTooLongNoteWithoutHtmxShowsTheWholeEditorPage() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    asAnna(
            post("/plan/slots/" + slot.id() + "/note")
                .param("version", String.valueOf(slot.version()))
                .param("note", "y".repeat(501)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("legfeljebb 500")));
  }

  @Test
  void aStaleNoteIsAConflictRetargetedIntoTheBanner() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/note")
            .param("version", String.valueOf(slot.version() + 5))
            .param("note", "késő"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"))
        .andExpect(header().string("HX-Reswap", "innerHTML"))
        .andExpect(content().string(containsString("Valaki más közben")))
        .andExpect(content().string(containsString("href=\"/plan?week=2020-W20\"")))
        .andExpect(content().string(not(containsString("<html"))));
    assertThat(current(slot.id()).note()).isNull();
  }

  @Test
  void aMissingVersionIsAConflictAndNeverASilentWrite() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/note").param("note", "x"))
        .andExpect(status().isConflict());
    assertThat(current(slot.id()).note()).isNull();
  }

  @Test
  void aStaleNoteWithoutHtmxAnswersTheWholeWeekWithTheBanner() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    String page =
        body(
            asAnna(
                    post("/plan/slots/" + slot.id() + "/note")
                        .param("version", "99")
                        .param("note", "késő"))
                .andExpect(status().isConflict()));

    assertThat(page)
        .contains("<html")
        .contains("20. hét")
        .contains("Valaki más közben módosította a tervet")
        .contains("data-add=\"2020-05-11/LUNCH\"");
  }

  // ---- move ----

  @Test
  void aSlotMovesToAnotherDayAndSlotType() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/move")
            .param("version", String.valueOf(slot.version()))
            .param("date", "2020-05-13")
            .param("slot", "DINNER"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_WEDNESDAY));

    SlotView moved = current(slot.id());
    assertThat(moved.date()).isEqualTo(LocalDate.of(2020, 5, 13));
    assertThat(moved.slotType()).isEqualTo(SlotType.DINNER);
  }

  @Test
  void aStaleMoveIsAConflictAndAMissingDateIsBad() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/move")
            .param("version", "99")
            .param("date", "2020-05-13")
            .param("slot", "DINNER"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"));
    htmx(post("/plan/slots/" + slot.id() + "/move").param("version", "0").param("slot", "DINNER"))
        .andExpect(status().isBadRequest());
    htmx(post("/plan/slots/" + slot.id() + "/move")
            .param("version", "0")
            .param("date", "2020-05-13")
            .param("slot", "LUNCHES"))
        .andExpect(status().isBadRequest());
    assertThat(current(slot.id()).slotType()).isEqualTo(SlotType.LUNCH);
  }

  // ---- reorder ----

  @Test
  void reorderSwapsThePositions() throws Exception {
    SlotView first = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());
    SlotView second = plan(MONDAY, SlotType.LUNCH, annasMeal("Pogácsa").id());

    htmx(post("/plan/slots/" + second.id() + "/reorder")
            .param("version", String.valueOf(second.version()))
            .param("position", "0"))
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY));

    assertThat(current(second.id()).position()).isZero();
    assertThat(current(first.id()).position()).isEqualTo(1);
  }

  @Test
  void aStaleReorderIsAConflict() throws Exception {
    SlotView first = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());
    plan(MONDAY, SlotType.LUNCH, annasMeal("Pogácsa").id());

    htmx(post("/plan/slots/" + first.id() + "/reorder")
            .param("version", "99")
            .param("position", "1"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"));
    assertThat(current(first.id()).position()).isZero();
  }

  // ---- remove ----

  @Test
  void removeTakesTheSlotOutOfThePlan() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/remove")
            .param("version", String.valueOf(slot.version())))
        .andExpect(header().string("HX-Redirect", REDIRECT_TO_MONDAY));

    assertThat(planning.week(anna, annasHome, MONDAY).slots()).isEmpty();
  }

  @Test
  void aStaleRemoveIsAConflictAndTheSlotStays() throws Exception {
    SlotView slot = plan(MONDAY, SlotType.LUNCH, annasMeal("Gulyásleves").id());

    htmx(post("/plan/slots/" + slot.id() + "/remove").param("version", "99"))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"));

    assertThat(planning.week(anna, annasHome, MONDAY).slots()).hasSize(1);
  }
}
