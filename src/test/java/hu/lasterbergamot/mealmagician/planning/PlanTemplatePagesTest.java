package hu.lasterbergamot.mealmagician.planning;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.LocalDate;
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
 * The plan template pages through the whole stack: the list, one template's page, and its rename
 * and delete actions, with their 404, 409 and 422 answers. Anna owns "Annas"; Bela owns "Belas" and
 * must never reach Anna's templates (PLAN.md D8). The week is a fixed one: Monday 2020-05-11. Texts
 * are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PlanTemplatePagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2020, 5, 11);
  private static final String STALE_TEXT = "Valaki más módosította ezt a sablont";

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private PlanTemplateService templates;
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

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  /** Plans one meal for Anna on Monday and saves that day as a template with this name. */
  private PlanTemplateView dayTemplate(String name) {
    long meal = meals.quickAdd(anna, annasHome, "Étel " + name).id();
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, meal, null);
    return templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, name);
  }

  // ---- the list ----

  @Test
  void theListGroupsTheHouseholdsTemplatesByScopeWithTheirMealCounts() throws Exception {
    long stew = meals.quickAdd(anna, annasHome, "Pörkölt").id();
    long soup = meals.quickAdd(anna, annasHome, "Leves").id();
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);
    planning.add(anna, annasHome, MONDAY.plusDays(6), SlotType.DINNER, soup, null);
    templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Annas nap");
    templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "Annas het");
    long belasMeal = meals.quickAdd(bela, belasHome, "Belas titok").id();
    planning.add(bela, belasHome, MONDAY, SlotType.LUNCH, belasMeal, null);
    templates.save(bela, belasHome, TemplateScope.DAY, MONDAY, "Belas sablon");

    String page = body(asAnna(get("/plan/templates")).andExpect(status().isOk()));

    assertThat(page)
        .contains("Napsablonok")
        .contains("Hétsablonok")
        .contains("Annas nap")
        .contains("Annas het")
        .contains("Ételek: 1")
        .contains("Ételek: 2")
        .doesNotContain("Belas sablon");
  }

  @Test
  void aHouseholdWithoutTemplatesGetsOneEmptyStateWithALinkToThePlan() throws Exception {
    asAnna(get("/plan/templates"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Még nincs sablon")))
        .andExpect(content().string(containsString("href=\"/plan\"")))
        .andExpect(content().string(not(containsString("Napsablonok"))));
  }

  // ---- the detail page ----

  @Test
  void aDayTemplateShowsItsMealsAndNotesAndTheRenameAndDeleteControls() throws Exception {
    long stew = meals.quickAdd(anna, annasHome, "Pörkölt").id();
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, "dupla adag");
    PlanTemplateView day = templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Annas nap");

    String page = body(asAnna(get("/plan/templates/" + day.id())).andExpect(status().isOk()));

    assertThat(page)
        .contains("Napsablon")
        .contains("A nap")
        .contains("Pörkölt")
        .contains("dupla adag")
        .contains("id=\"template-rename-form\"")
        .contains("id=\"delete-template\"")
        .contains("/plan/templates/" + day.id() + "/delete?version=" + day.version());
  }

  @Test
  void aWeekTemplateNamesTheWeekdays() throws Exception {
    planning.add(
        anna,
        annasHome,
        MONDAY,
        SlotType.LUNCH,
        meals.quickAdd(anna, annasHome, "Pörkölt").id(),
        null);
    planning.add(
        anna,
        annasHome,
        MONDAY.plusDays(6),
        SlotType.DINNER,
        meals.quickAdd(anna, annasHome, "Leves").id(),
        null);
    PlanTemplateView week =
        templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "Annas het");

    String page = body(asAnna(get("/plan/templates/" + week.id())).andExpect(status().isOk()));

    assertThat(page)
        .contains("Hétsablon")
        .contains("Hétfő")
        .contains("Vasárnap")
        .doesNotContain("A nap");
  }

  @Test
  void anArchivedMealOnATemplateIsMarkedArchived() throws Exception {
    long stew = meals.quickAdd(anna, annasHome, "Pörkölt").id();
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);
    PlanTemplateView day = templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Annas nap");
    meals.archive(anna, annasHome, stew);

    asAnna(get("/plan/templates/" + day.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Archivált")));
  }

  @Test
  void anotherHouseholdsTemplateIsNotFoundAndIsNotChanged() throws Exception {
    long belasMeal = meals.quickAdd(bela, belasHome, "Belas titok").id();
    planning.add(bela, belasHome, MONDAY, SlotType.LUNCH, belasMeal, null);
    PlanTemplateView belas =
        templates.save(bela, belasHome, TemplateScope.DAY, MONDAY, "Belas sablon");
    String base = "/plan/templates/" + belas.id();

    asAnna(get(base)).andExpect(status().isNotFound());
    asAnna(
            post(base + "/rename")
                .param("version", String.valueOf(belas.version()))
                .param("name", "x"))
        .andExpect(status().isNotFound());
    asAnna(post(base + "/delete").param("version", String.valueOf(belas.version())))
        .andExpect(status().isNotFound());
    assertThat(templates.find(bela, belasHome, belas.id()).name()).isEqualTo("Belas sablon");
  }

  // ---- rename ----

  @Test
  void aRenameWithHtmxRedirectsBackToTheTemplateAndTheNameChanges() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    htmx(post("/plan/templates/" + day.id() + "/rename")
            .param("version", String.valueOf(day.version()))
            .param("name", "Új név"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/plan/templates/" + day.id()));

    assertThat(templates.find(anna, annasHome, day.id()).name()).isEqualTo("Új név");
  }

  @Test
  void aRenameWithoutHtmxRedirectsWithSeeOther() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    asAnna(
            post("/plan/templates/" + day.id() + "/rename")
                .param("version", String.valueOf(day.version()))
                .param("name", "Új név"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/plan/templates/" + day.id()));
  }

  @Test
  void aBlankRenameIsRefusedWithTheFormAndNothingChanges() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    String form =
        body(
            htmx(post("/plan/templates/" + day.id() + "/rename")
                    .param("version", String.valueOf(day.version()))
                    .param("name", "  "))
                .andExpect(status().isUnprocessableContent()));

    assertThat(form)
        .contains("id=\"template-rename-form\"")
        .contains("Adj nevet a sablonnak.")
        .doesNotContain("<html");
    assertThat(templates.find(anna, annasHome, day.id()).name()).isEqualTo("Annas nap");
  }

  @Test
  void aTooLongRenameKeepsTheTypedTextAndSaysTheLimit() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");
    String name = "x".repeat(101);

    htmx(post("/plan/templates/" + day.id() + "/rename")
            .param("version", String.valueOf(day.version()))
            .param("name", name))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("legfeljebb 100")))
        .andExpect(content().string(containsString(name)));
  }

  @Test
  void aBlankRenameWithoutHtmxShowsTheWholePage() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    asAnna(
            post("/plan/templates/" + day.id() + "/rename")
                .param("version", String.valueOf(day.version()))
                .param("name", ""))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Adj nevet a sablonnak.")));
  }

  @Test
  void aStaleRenameIsAWarningAndKeepsTheTypedName() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");
    templates.rename(anna, annasHome, day.id(), day.version(), "Mással átnevezve");

    String form =
        body(
            htmx(post("/plan/templates/" + day.id() + "/rename")
                    .param("version", String.valueOf(day.version()))
                    .param("name", "Az én nevem"))
                .andExpect(status().isConflict()));

    assertThat(form)
        .contains("id=\"template-rename-form\"")
        .contains(STALE_TEXT)
        .contains("value=\"Az én nevem\"")
        .doesNotContain("<html");
    assertThat(templates.find(anna, annasHome, day.id()).name()).isEqualTo("Mással átnevezve");
  }

  // ---- delete ----

  @Test
  void aDeleteWithHtmxGoesBackToTheListAndTheTemplateIsGone() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    htmx(post("/plan/templates/" + day.id() + "/delete")
            .param("version", String.valueOf(day.version())))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/plan/templates"));

    assertThatThrownBy(() -> templates.find(anna, annasHome, day.id()))
        .isInstanceOf(TemplateNotFoundException.class);
  }

  @Test
  void aDeleteWithoutHtmxGoesBackToTheListWithSeeOther() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    asAnna(
            post("/plan/templates/" + day.id() + "/delete")
                .param("version", String.valueOf(day.version())))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/plan/templates"));
  }

  @Test
  void aStaleDeleteWithHtmxRetargetsTheBannerIntoThePlanConflictSlot() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");
    templates.rename(anna, annasHome, day.id(), day.version(), "Új");

    htmx(post("/plan/templates/" + day.id() + "/delete")
            .param("version", String.valueOf(day.version())))
        .andExpect(status().isConflict())
        .andExpect(header().string("HX-Retarget", "#plan-conflict"))
        .andExpect(header().string("HX-Reswap", "innerHTML"))
        .andExpect(content().string(containsString(STALE_TEXT)))
        .andExpect(content().string(containsString("href=\"/plan/templates/" + day.id() + "\"")))
        .andExpect(content().string(not(containsString("<html"))));
    assertThat(templates.find(anna, annasHome, day.id()).name()).isEqualTo("Új");
  }

  @Test
  void aStaleDeleteWithoutHtmxShowsTheWholePageWithTheBanner() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");
    templates.rename(anna, annasHome, day.id(), day.version(), "Új");

    asAnna(
            post("/plan/templates/" + day.id() + "/delete")
                .param("version", String.valueOf(day.version())))
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString(STALE_TEXT)));
  }

  @Test
  void aDeleteWithoutAVersionIsAConflictAndNothingIsDeleted() throws Exception {
    PlanTemplateView day = dayTemplate("Annas nap");

    htmx(post("/plan/templates/" + day.id() + "/delete")).andExpect(status().isConflict());

    assertThat(templates.find(anna, annasHome, day.id()).name()).isEqualTo("Annas nap");
  }
}
