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

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
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
 * The occasion menu routes through the whole stack (issue #113): the pick sheet, add, quick add,
 * move, remove, and "Save as a template". Anna owns "Annas"; Bela owns "Belas" and must never reach
 * Anna's data (PLAN.md D8). Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class OccasionMenuPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private OccasionService occasions;
  @Autowired private OccasionTemplateService templates;
  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long occasionId;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    occasionId = occasion("Karácsony").id();
  }

  private OccasionView occasion(String name) {
    return occasions.create(
        anna,
        annasHome,
        new OccasionDetails(name, LocalDate.of(2030, 12, 24), OccasionType.HOLIDAY, null));
  }

  private MealView annasMeal(String name) {
    return meals.quickAdd(anna, annasHome, name);
  }

  private OccasionView withMeals(long id, String... names) {
    OccasionView view = null;
    for (String name : names) {
      view = occasions.addItem(anna, annasHome, id, annasMeal(name).id());
    }
    return view;
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

  private static int count(String text, String part) {
    return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
  }

  private List<String> menuNames() {
    return occasions.find(anna, annasHome, occasionId).menu().stream()
        .map(item ->
            meals.search(anna, annasHome, new MealSearch(null, null, true)).stream()
                .filter(m -> m.id() == item.mealId())
                .findFirst()
                .orElseThrow()
                .name())
        .toList();
  }

  // ---- the pick sheet ----

  @Test
  void thePickSheetListsThePlannedMealFirstThenByName() throws Exception {
    annasMeal("Bableves");
    annasMeal("alma");
    MealView planned = annasMeal("Zserbó");
    planning.add(anna, annasHome, LocalDate.of(2020, 5, 11), SlotType.LUNCH, planned.id(), null);

    String sheet =
        body(
            htmx(get("/occasions/" + occasionId + "/pick"))
                .andExpect(status().isOk())
                .andExpect(header().string("Vary", containsString("HX-Request")))
                .andExpect(content().string(not(containsString("<html")))));

    assertThat(sheet.indexOf("Zserbó")).isPositive().isLessThan(sheet.indexOf("alma"));
    assertThat(sheet.indexOf("alma")).isLessThan(sheet.indexOf("Bableves"));
    assertThat(sheet)
        .contains("id=\"occasion-pick-quick-form\"")
        .contains("id=\"occasion-pick-results\"")
        .contains("action=\"/occasions/" + occasionId + "/menu\"");
  }

  @Test
  void archivedAndOtherHouseholdsMealsAreNotOffered() throws Exception {
    MealView old = annasMeal("Régi rántotta");
    meals.archive(anna, annasHome, old.id());
    meals.quickAdd(bela, belasHome, "Belas titok");
    annasMeal("Friss saláta");

    htmx(get("/occasions/" + occasionId + "/pick"))
        .andExpect(content().string(containsString("Friss saláta")))
        .andExpect(content().string(not(containsString("Régi rántotta"))))
        .andExpect(content().string(not(containsString("Belas titok"))));
  }

  @Test
  void theSearchFiltersTheLibrary() throws Exception {
    annasMeal("Gulyásleves");
    annasMeal("Palacsinta");

    htmx(get("/occasions/" + occasionId + "/pick").param("q", "leves"))
        .andExpect(content().string(containsString("Gulyásleves")))
        .andExpect(content().string(not(containsString("Palacsinta"))));
  }

  @Test
  void aLongLibraryShowsTwentyMealsAndSaysSo() throws Exception {
    for (int i = 1; i <= 21; i++) {
      annasMeal("Étel %02d".formatted(i));
    }

    String sheet = body(htmx(get("/occasions/" + occasionId + "/pick")));

    assertThat(count(sheet, "name=\"mealId\"")).isEqualTo(20);
    assertThat(sheet).contains("Az első 20 étel látszik");
  }

  @Test
  void anEmptyLibraryAndAMissedSearchSaySo() throws Exception {
    htmx(get("/occasions/" + occasionId + "/pick"))
        .andExpect(content().string(containsString("Még üres az ételkönyvtárad")));
    annasMeal("Palacsinta");
    htmx(get("/occasions/" + occasionId + "/pick").param("q", "zzz"))
        .andExpect(content().string(containsString("Nincs ilyen étel")));
  }

  @Test
  void withoutHtmxThePickPageIsAWholePageWithABackLink() throws Exception {
    asAnna(get("/occasions/" + occasionId + "/pick"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Vissza az alkalomhoz")))
        .andExpect(content().string(containsString("href=\"/occasions/" + occasionId + "\"")));
  }

  @Test
  void anotherHouseholdsOccasionHasNoPickSheet() throws Exception {
    long belas =
        occasions
            .create(
                bela,
                belasHome,
                new OccasionDetails("Belas", LocalDate.of(2030, 1, 1), OccasionType.OTHER, null))
            .id();

    asAnna(get("/occasions/" + belas + "/pick")).andExpect(status().isNotFound());
  }

  // ---- add ----

  @Test
  void addingALibraryMealRedirectsToThePageAndRefreshesWhenAlreadyThere() throws Exception {
    MealView stew = annasMeal("Pörkölt");

    htmx(post("/occasions/" + occasionId + "/menu")
            .param("mealId", String.valueOf(stew.id()))
            .header("HX-Current-URL", "http://localhost/occasions/" + occasionId))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/occasions/" + occasionId))
        .andExpect(header().string("HX-Refresh", "true"));

    assertThat(menuNames()).containsExactly("Pörkölt");
  }

  @Test
  void withoutTheCurrentUrlThereIsNoRefresh() throws Exception {
    MealView stew = annasMeal("Pörkölt");

    htmx(post("/occasions/" + occasionId + "/menu").param("mealId", String.valueOf(stew.id())))
        .andExpect(header().string("HX-Redirect", "/occasions/" + occasionId))
        .andExpect(header().doesNotExist("HX-Refresh"));
  }

  @Test
  void aPlainPostRedirectsWithoutRefresh() throws Exception {
    MealView stew = annasMeal("Pörkölt");

    asAnna(post("/occasions/" + occasionId + "/menu").param("mealId", String.valueOf(stew.id())))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "/occasions/" + occasionId))
        .andExpect(header().doesNotExist("HX-Refresh"));
  }

  @Test
  void anotherHouseholdsMealIsNotFoundAndAMissingOneIsBad() throws Exception {
    MealView belas = meals.quickAdd(bela, belasHome, "Belas titok");

    asAnna(post("/occasions/" + occasionId + "/menu").param("mealId", String.valueOf(belas.id())))
        .andExpect(status().isNotFound());
    asAnna(post("/occasions/" + occasionId + "/menu")).andExpect(status().isBadRequest());
    asAnna(post("/occasions/" + occasionId + "/menu").param("mealId", "x"))
        .andExpect(status().isBadRequest());
  }

  // ---- quick add ----

  @Test
  void quickAddCreatesTheMealAndPutsItOnTheMenu() throws Exception {
    htmx(post("/occasions/" + occasionId + "/menu/quick").param("name", "Torta"))
        .andExpect(header().string("HX-Redirect", "/occasions/" + occasionId));

    assertThat(menuNames()).containsExactly("Torta");
  }

  @Test
  void aBlankOrTooLongQuickNameIsRefusedAndKept() throws Exception {
    htmx(post("/occasions/" + occasionId + "/menu/quick").param("name", ""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("id=\"occasion-pick-quick-form\"")))
        .andExpect(content().string(containsString("Adj nevet az ételnek.")));
    htmx(post("/occasions/" + occasionId + "/menu/quick").param("name", "x".repeat(201)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("legfeljebb 200")))
        .andExpect(content().string(containsString("value=\"" + "x".repeat(201) + "\"")));
  }

  @Test
  void aRejectedQuickNameWithoutHtmxIsTheWholePickPage() throws Exception {
    asAnna(post("/occasions/" + occasionId + "/menu/quick").param("name", ""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Adj nevet az ételnek.")));
  }

  // ---- move and remove ----

  @Test
  void movingALineUpSwapsTheOrder() throws Exception {
    OccasionView view = withMeals(occasionId, "Leves", "Főétel");
    long second = view.menu().get(1).id();

    htmx(post("/occasions/" + occasionId + "/menu/" + second + "/move").param("position", "0"))
        .andExpect(header().string("HX-Redirect", "/occasions/" + occasionId));

    assertThat(menuNames()).containsExactly("Főétel", "Leves");
  }

  @Test
  void removingALineRenumbersTheOthers() throws Exception {
    OccasionView view = withMeals(occasionId, "Leves", "Főétel", "Desszert");
    long first = view.menu().get(0).id();

    htmx(post("/occasions/" + occasionId + "/menu/" + first + "/remove"))
        .andExpect(header().string("HX-Redirect", "/occasions/" + occasionId));

    OccasionView after = occasions.find(anna, annasHome, occasionId);
    assertThat(menuNames()).containsExactly("Főétel", "Desszert");
    assertThat(after.menu()).extracting(MenuItemView::position).containsExactly(0, 1);
  }

  @Test
  void anItemOfAnotherOccasionIsNotFound() throws Exception {
    long other = occasion("Húsvét").id();
    long foreign = withMeals(other, "Sonka").menu().get(0).id();

    asAnna(post("/occasions/" + occasionId + "/menu/" + foreign + "/remove"))
        .andExpect(status().isNotFound());
    asAnna(post("/occasions/" + occasionId + "/menu/" + foreign + "/move").param("position", "0"))
        .andExpect(status().isNotFound());
  }

  // ---- the detail page ----

  @Test
  void theDetailPageOffersTheMenuControlsAndTheSaveForm() throws Exception {
    OccasionView view = withMeals(occasionId, "Leves", "Főétel");
    long first = view.menu().get(0).id();
    long second = view.menu().get(1).id();

    String page = body(asAnna(get("/occasions/" + occasionId)).andExpect(status().isOk()));

    assertThat(page)
        .contains("id=\"occasion-add-meal\"")
        .contains("id=\"occasion-pick-sheet\"")
        .contains("id=\"remove-item-" + first + "\"")
        .contains("id=\"remove-item-" + second + "\"")
        .contains("id=\"occasion-save-template-form\"")
        .contains("value=\"Karácsony\"");
    assertThat(count(page, "Feljebb")).as("up only on line 2").isEqualTo(1);
    assertThat(count(page, "Lejjebb")).as("down only on line 1").isEqualTo(1);
    assertThat(page.indexOf("/menu/" + second + "/move"))
        .isGreaterThan(page.indexOf("data-item-id=\"" + second + "\""));
  }

  @Test
  void anEmptyMenuHasNoSaveForm() throws Exception {
    asAnna(get("/occasions/" + occasionId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"occasion-add-meal\"")))
        .andExpect(content().string(not(containsString("occasion-save-template-form"))));
  }

  // ---- save as a template ----

  @Test
  void savingTheMenuRedirectsToTheNewTemplate() throws Exception {
    withMeals(occasionId, "Leves");

    htmx(post("/occasions/" + occasionId + "/template").param("name", "Karácsonyi menü"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", containsString("/occasions/templates/")));

    List<OccasionTemplateSummary> saved = templates.list(anna, annasHome);
    assertThat(saved).extracting(OccasionTemplateSummary::name).containsExactly("Karácsonyi menü");
  }

  @Test
  void aBlankOrLongTemplateNameIsRefusedAndKept() throws Exception {
    withMeals(occasionId, "Leves");

    htmx(post("/occasions/" + occasionId + "/template").param("name", " "))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("id=\"occasion-save-template-form\"")))
        .andExpect(content().string(containsString("Adj nevet a sablonnak.")));
    htmx(post("/occasions/" + occasionId + "/template").param("name", "x".repeat(101)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("legfeljebb 100")));
    assertThat(templates.list(anna, annasHome)).isEmpty();
  }

  @Test
  void anEmptyMenuCannotBeSaved() throws Exception {
    htmx(post("/occasions/" + occasionId + "/template").param("name", "Üres"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Előbb tegyél legalább egy ételt a menüre.")));
  }

  @Test
  void aRejectedSaveWithoutHtmxIsTheWholeDetailPage() throws Exception {
    asAnna(post("/occasions/" + occasionId + "/template").param("name", ""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("Karácsony")));
  }

  @Test
  void anotherHouseholdsOccasionCannotBeChangedOrSaved() throws Exception {
    long belas =
        occasions
            .create(
                bela,
                belasHome,
                new OccasionDetails("Belas", LocalDate.of(2030, 1, 1), OccasionType.OTHER, null))
            .id();

    asAnna(post("/occasions/" + belas + "/menu").param("mealId", "1"))
        .andExpect(status().isNotFound());
    asAnna(post("/occasions/" + belas + "/menu/quick").param("name", "X"))
        .andExpect(status().isNotFound());
    asAnna(post("/occasions/" + belas + "/template").param("name", "X"))
        .andExpect(status().isNotFound());
    asAnna(post("/occasions/" + belas + "/menu/1/remove")).andExpect(status().isNotFound());
  }
}
