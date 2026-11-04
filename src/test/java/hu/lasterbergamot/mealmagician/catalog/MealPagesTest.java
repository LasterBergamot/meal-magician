package hu.lasterbergamot.mealmagician.catalog;

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

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Clock;
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
 * The meal pages through the whole stack (security, controllers, database, templates). Anna owns
 * "Annas"; Bela owns "Belas" and must never see Anna's meals (PLAN.md D8); the stranger is in no
 * household. Texts are the Hungarian ones, the app's default language.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MealPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long stranger;
  private long annasHome;
  private long belasHome;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
  }

  private ResultActions asAnna(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(anna)).with(csrf()));
  }

  private MealView add(String name, String... tags) {
    return meals.create(anna, annasHome, new MealDetails(name, null, null, List.of(tags)));
  }

  // ---- the list ----

  @Test
  void anEmptyLibraryInvitesTheFirstMeal() throws Exception {
    asAnna(get("/meals"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Még nincs étel")))
        .andExpect(content().string(containsString("href=\"/meals/new\"")))
        .andExpect(header().string("Vary", "HX-Request"));
  }

  @Test
  void theListShowsMealsWithTagsAndTheQuickAddForms() throws Exception {
    add("Lentil soup", "vegan");

    asAnna(get("/meals"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Lentil soup")))
        .andExpect(content().string(containsString("vegan")))
        .andExpect(content().string(containsString("id=\"quick-bar-form\"")))
        .andExpect(content().string(containsString("id=\"quick-sheet-form\"")))
        .andExpect(content().string(containsString("href=\"/meals/new\"")));
  }

  @Test
  void anotherHouseholdsMealsAreNotListed() throws Exception {
    add("Annas secret stew");

    mvc.perform(get("/meals").with(signedInAs(bela)))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Annas secret stew"))));
  }

  @Test
  void searchingByNameKeepsOnlyTheMatchingMeals() throws Exception {
    add("Lentil soup");
    add("Pasta");

    asAnna(get("/meals").param("q", "SOUP"))
        .andExpect(content().string(containsString("Lentil soup")))
        .andExpect(content().string(not(containsString(">Pasta<"))));
  }

  @Test
  void aSearchWithoutResultsSaysSoAndOffersToClearTheFilters() throws Exception {
    add("Pasta");

    asAnna(get("/meals").param("q", "zzz"))
        .andExpect(content().string(containsString("Nincs találat")))
        .andExpect(content().string(containsString("href=\"/meals\"")));
  }

  @Test
  void filteringByTagMarksTheChipPressedAndPressingItAgainClearsTheTag() throws Exception {
    add("Lentil soup", "vegan");
    add("Pasta", "quick");

    asAnna(get("/meals").param("tag", "vegan"))
        .andExpect(content().string(containsString("Lentil soup")))
        .andExpect(content().string(not(containsString(">Pasta<"))))
        .andExpect(content().string(containsString("aria-pressed=\"true\"")))
        // The pressed chip links to the list without the tag.
        .andExpect(content().string(containsString("hx-get=\"/meals\"")));
  }

  @Test
  void archivedMealsAppearOnlyWhenAsked() throws Exception {
    MealView old = add("Old stew");
    meals.archive(anna, annasHome, old.id());

    asAnna(get("/meals")).andExpect(content().string(not(containsString("Old stew"))));
    asAnna(get("/meals").param("archived", "true"))
        .andExpect(content().string(containsString("Old stew")))
        .andExpect(content().string(containsString("Archivált")))
        .andExpect(content().string(containsString("name=\"archived\" value=\"true\"")));
  }

  @Test
  void theRecentSortListsPlannedMealsFirstThenTheNewestAdditions() throws Exception {
    MealView planned = add("Planned first");
    add("Added second");
    add("Added third");
    planning.add(anna, annasHome, LocalDate.now(clock), SlotType.LUNCH, planned.id(), null);

    String page = asAnna(get("/meals")).andReturn().getResponse().getContentAsString();

    assertThat(page.indexOf("Planned first"))
        .isLessThan(page.indexOf("Added third"))
        .isGreaterThan(-1);
    assertThat(page.indexOf("Added third")).isLessThan(page.indexOf("Added second"));
  }

  @Test
  void theNameSortIsAlphabetical() throws Exception {
    MealView planned = add("Zucchini");
    add("Apple pie");
    planning.add(anna, annasHome, LocalDate.now(clock), SlotType.LUNCH, planned.id(), null);

    String page =
        asAnna(get("/meals").param("sort", "name")).andReturn().getResponse().getContentAsString();

    assertThat(page.indexOf("Apple pie")).isLessThan(page.indexOf("Zucchini"));
  }

  @Test
  void htmxGetsOnlyTheResults() throws Exception {
    add("Lentil soup");

    asAnna(get("/meals").header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Lentil soup")))
        .andExpect(content().string(not(containsString("<html"))))
        .andExpect(content().string(not(containsString("id=\"meal-q\""))));
  }

  @Test
  void aPersonInNoHouseholdGetsNotFound() throws Exception {
    mvc.perform(get("/meals").with(signedInAs(stranger))).andExpect(status().isNotFound());
  }

  // ---- the detail page ----

  @Test
  void theDetailPageShowsEverythingAndOpensTheRecipeSafely() throws Exception {
    MealView cake =
        meals.create(
            anna,
            annasHome,
            new MealDetails(
                "Cake",
                "Line one",
                "https://example.com/cake?a=1&b=2",
                List.of("sweet"),
                List.of(OccasionType.BIRTHDAY)));

    asAnna(get("/meals/" + cake.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Cake")))
        .andExpect(content().string(containsString("Line one")))
        .andExpect(content().string(containsString("rel=\"noopener noreferrer\"")))
        .andExpect(
            content().string(containsString("href=\"https://example.com/cake?a=1&amp;b=2\"")))
        .andExpect(content().string(containsString("sweet")))
        .andExpect(content().string(containsString("Születésnap")))
        .andExpect(content().string(containsString("/meals/" + cake.id() + "/archive")))
        .andExpect(content().string(containsString("/meals/" + cake.id() + "/edit")));
  }

  @Test
  void aMealWithoutDetailsShowsDashesNotEmptyBlocks() throws Exception {
    MealView plain = add("Toast");

    asAnna(get("/meals/" + plain.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("rel=\"noopener"))));
  }

  @Test
  void anArchivedMealOffersRestoreInsteadOfArchive() throws Exception {
    MealView old = add("Old stew");
    meals.archive(anna, annasHome, old.id());

    asAnna(get("/meals/" + old.id()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Ez az étel archiválva van")))
        .andExpect(content().string(containsString("/meals/" + old.id() + "/restore")))
        .andExpect(content().string(not(containsString("/meals/" + old.id() + "/archive"))));
  }

  @Test
  void anotherHouseholdsMealIsNotFoundEverywhere() throws Exception {
    MealView annas = add("Annas stew");

    mvc.perform(get("/meals/" + annas.id()).with(signedInAs(bela)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/meals/" + annas.id() + "/edit").with(signedInAs(bela)))
        .andExpect(status().isNotFound());
    for (String path : List.of("", "/archive", "/restore")) {
      mvc.perform(post("/meals/" + annas.id() + path).with(signedInAs(bela)).with(csrf()))
          .andExpect(status().isNotFound());
    }
    assertThat(meals.find(anna, annasHome, annas.id()).archived()).isFalse();
  }

  // ---- the form ----

  @Test
  void theNewMealFormIsEmptyAndShowsEveryOccasionType() throws Exception {
    asAnna(get("/meals/new"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"meal-form\"")))
        .andExpect(content().string(containsString("action=\"/meals\"")))
        .andExpect(content().string(containsString("value=\"BIRTHDAY\"")))
        .andExpect(content().string(containsString("value=\"OTHER\"")))
        .andExpect(content().string(not(containsString("name=\"version\""))));
  }

  @Test
  void htmxGetsOnlyTheFormFragment() throws Exception {
    asAnna(get("/meals/new").header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"meal-form\"")))
        .andExpect(content().string(not(containsString("<html"))));
  }

  @Test
  void aValidFormCreatesTheMealAndGoesBackToTheList() throws Exception {
    asAnna(
            post("/meals")
                .param("name", "  Chicken paprikash ")
                .param("notes", "Sour cream at the end")
                .param("recipeUrl", "https://example.com/paprikash")
                .param("tags", "dinner, , Hungarian,dinner")
                .param("occasion", "HOLIDAY", "PARTY"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/meals"));

    MealView saved = meals.search(anna, annasHome, new MealSearch("paprikash", null, false)).get(0);
    assertThat(saved.name()).isEqualTo("Chicken paprikash");
    assertThat(saved.tags()).containsExactly("dinner", "Hungarian");
    assertThat(saved.occasionTypes()).containsExactly(OccasionType.HOLIDAY, OccasionType.PARTY);
  }

  @Test
  void anHtmxFormPostRedirectsWithAHeader() throws Exception {
    asAnna(post("/meals").header("HX-Request", "true").param("name", "Soup"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/meals"));
  }

  @Test
  void aBlankNameGives422AndKeepsWhatWasTyped() throws Exception {
    asAnna(post("/meals").param("name", "  ").param("notes", "keep me").param("occasion", "PARTY"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("Adj nevet az ételnek.")))
        .andExpect(content().string(containsString("aria-invalid=\"true\"")))
        .andExpect(content().string(containsString("keep me")))
        .andExpect(content().string(containsString("value=\"PARTY\" checked")));
    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, false))).isEmpty();
  }

  @Test
  void eachRuleBreakShowsItsMessageUnderTheRightField() throws Exception {
    asAnna(post("/meals").param("name", "Soup").param("recipeUrl", "javascript:alert(1)"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"meal-recipe-error\"")));
    asAnna(post("/meals").param("name", "Soup").param("notes", "x".repeat(2001)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"meal-notes-error\"")));
    asAnna(post("/meals").param("name", "Soup").param("tags", "x".repeat(51)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"meal-tags-error\"")));
    asAnna(post("/meals").param("name", "x".repeat(201)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"meal-name-error\"")));
  }

  @Test
  void aRejectedHtmxFormReturnsOnlyTheFormFragment() throws Exception {
    asAnna(post("/meals").header("HX-Request", "true").param("name", ""))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"meal-form\"")))
        .andExpect(content().string(not(containsString("<html"))));
  }

  @Test
  void theEditFormHoldsTheStoredValuesAndTheVersion() throws Exception {
    MealView cake =
        meals.create(
            anna,
            annasHome,
            new MealDetails(
                "Cake",
                "Notes",
                "https://example.com/c",
                List.of("a", "b"),
                List.of(OccasionType.PARTY)));

    asAnna(get("/meals/" + cake.id() + "/edit"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("action=\"/meals/" + cake.id() + "\"")))
        .andExpect(content().string(containsString("name=\"version\" value=\"0\"")))
        .andExpect(content().string(containsString("value=\"a, b\"")))
        .andExpect(content().string(containsString("value=\"https://example.com/c\"")))
        .andExpect(content().string(containsString("value=\"PARTY\" checked")));
  }

  @Test
  void savingAnEditChangesTheMealAndShowsIt() throws Exception {
    MealView stew = add("Stew", "old");

    asAnna(
            post("/meals/" + stew.id())
                .param("version", String.valueOf(stew.version()))
                .param("name", "Beef stew")
                .param("tags", "new"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/meals/" + stew.id()));

    MealView saved = meals.find(anna, annasHome, stew.id());
    assertThat(saved.name()).isEqualTo("Beef stew");
    assertThat(saved.tags()).containsExactly("new");
  }

  @Test
  void aRejectedEditGives422WithTheVersionStillInTheForm() throws Exception {
    MealView stew = add("Stew");

    asAnna(post("/meals/" + stew.id()).param("version", "0").param("name", ""))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
    assertThat(meals.find(anna, annasHome, stew.id()).name()).isEqualTo("Stew");
  }

  @Test
  void anEditFromAStaleFormGives409AndASaveIsNotMade() throws Exception {
    MealView stew = add("Stew");
    meals.edit(anna, annasHome, stew.id(), new MealDetails("Stew by Bela", null, null, null));

    asAnna(post("/meals/" + stew.id()).param("version", "0").param("name", "My stew"))
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("Valaki más módosította ezt az ételt")))
        .andExpect(content().string(containsString("My stew")));
    assertThat(meals.find(anna, annasHome, stew.id()).name()).isEqualTo("Stew by Bela");
  }

  // ---- ingredients ----

  @Test
  void aFormPostSavesTheChipsAndTheCardAndDetailShowThem() throws Exception {
    asAnna(post("/meals").param("name", "Omelette").param("ingredients", "Tojás", "Salt, coarse"))
        .andExpect(status().isSeeOther());

    MealView saved = meals.search(anna, annasHome, new MealSearch("omelette", null, false)).get(0);
    assertThat(saved.ingredients()).containsExactlyInAnyOrder("Tojás", "Salt, coarse");
    asAnna(get("/meals"))
        .andExpect(content().string(containsString("Tojás")))
        .andExpect(content().string(containsString("Salt, coarse")));
    asAnna(get("/meals/" + saved.id()))
        .andExpect(content().string(containsString("Hozzávalók")))
        .andExpect(content().string(containsString("Tojás")));
  }

  @Test
  void aSingleIngredientWithACommaIsNotSplit() throws Exception {
    asAnna(post("/meals").param("name", "Soup").param("ingredients", "Salt, coarse"))
        .andExpect(status().isSeeOther());

    assertThat(
            meals.search(anna, annasHome, new MealSearch("soup", null, false)).get(0).ingredients())
        .containsExactly("Salt, coarse");
  }

  @Test
  void aTooLongIngredientGives422KeepsTheOtherChipsAndShowsTheMessage() throws Exception {
    asAnna(post("/meals").param("name", "Soup").param("ingredients", "Tojás", "x".repeat(61)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"meal-ingredients-error\"")))
        .andExpect(content().string(containsString("Egy hozzávaló 1–60 karakter lehet")))
        .andExpect(content().string(containsString("name=\"ingredients\" value=\"Tojás\"")))
        .andExpect(content().string(containsString("value=\"" + "x".repeat(61) + "\"")));
    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, false))).isEmpty();
  }

  @Test
  void aStaleEditStillKeepsTheChips() throws Exception {
    MealView stew = add("Stew");
    meals.edit(anna, annasHome, stew.id(), new MealDetails("Stew by Bela", null, null, null));

    asAnna(
            post("/meals/" + stew.id())
                .param("version", "0")
                .param("name", "My stew")
                .param("ingredients", "Tojás"))
        .andExpect(status().isConflict())
        .andExpect(content().string(containsString("name=\"ingredients\" value=\"Tojás\"")));
  }

  @Test
  void anEditReplacesTheIngredients() throws Exception {
    MealView stew =
        meals.create(
            anna,
            annasHome,
            new MealDetails("Stew", null, null, null, null, List.of("Tej", "Vaj")));

    asAnna(
            post("/meals/" + stew.id())
                .param("version", String.valueOf(stew.version()))
                .param("name", "Stew")
                .param("ingredients", "Vaj"))
        .andExpect(status().isSeeOther());

    assertThat(meals.find(anna, annasHome, stew.id()).ingredients()).containsExactly("Vaj");
  }

  @Test
  void theListOffersIngredientChipsOnlyWhenSomeMealHasOneAndFiltersByThem() throws Exception {
    add("Plain");
    asAnna(get("/meals"))
        .andExpect(content().string(not(containsString("Szűrés hozzávaló szerint"))));

    meals.create(
        anna, annasHome, new MealDetails("Omelette", null, null, null, null, List.of("Tojás")));
    meals.create(
        anna, annasHome, new MealDetails("Pancake", null, null, null, null, List.of("Tej")));

    asAnna(get("/meals").param("ingredient", "tojas"))
        .andExpect(content().string(containsString("Szűrés hozzávaló szerint")))
        .andExpect(content().string(containsString("Omelette")))
        .andExpect(content().string(not(containsString(">Pancake<"))))
        // The chosen chip is pressed and links to the list without the filter.
        .andExpect(content().string(containsString("name=\"ingredient\" value=\"tojas\"")))
        .andExpect(content().string(containsString("hx-get=\"/meals?ingredient=Tej\"")));
    asAnna(get("/meals").param("ingredient", "Tojás"))
        .andExpect(content().string(containsString("hx-get=\"/meals\"")))
        .andExpect(content().string(containsString("aria-pressed=\"true\"")));
  }

  // ---- quick add, archive, restore ----

  @Test
  void quickAddSavesTheNameAndGoesBackToTheList() throws Exception {
    asAnna(post("/meals/quick").param("name", " Omelette "))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/meals"));

    assertThat(meals.search(anna, annasHome, new MealSearch("omelette", null, false)))
        .extracting(MealView::name)
        .containsExactly("Omelette");
  }

  @Test
  void aRejectedQuickAddFromHtmxReturnsTheSameSmallFormWith422() throws Exception {
    asAnna(
            post("/meals/quick")
                .header("HX-Request", "true")
                .param("form", "quick-sheet")
                .param("name", " "))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"quick-sheet-form\"")))
        .andExpect(content().string(containsString("Adj nevet az ételnek.")))
        .andExpect(content().string(not(containsString("<html"))));
  }

  @Test
  void anUnknownQuickAddVariantFallsBackToTheBar() throws Exception {
    asAnna(
            post("/meals/quick")
                .header("HX-Request", "true")
                .param("form", "<script>")
                .param("name", ""))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("id=\"quick-bar-form\"")));
  }

  @Test
  void aRejectedQuickAddWithoutHtmxFallsBackToTheFullFormWithTheNameKept() throws Exception {
    asAnna(post("/meals/quick").param("name", "x".repeat(201)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().string(containsString("<html")))
        .andExpect(content().string(containsString("id=\"meal-form\"")))
        .andExpect(content().string(containsString("A név legfeljebb 200 karakter lehet.")));
  }

  @Test
  void archivingHidesTheMealAndRestoringBringsItBack() throws Exception {
    MealView stew = add("Stew");

    asAnna(post("/meals/" + stew.id() + "/archive").header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(header().string("HX-Redirect", "/meals"));
    assertThat(meals.find(anna, annasHome, stew.id()).archived()).isTrue();

    asAnna(post("/meals/" + stew.id() + "/restore"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/meals/" + stew.id()));
    assertThat(meals.find(anna, annasHome, stew.id()).archived()).isFalse();
  }

  @Test
  void aPostWithoutTheCsrfTokenIsRefused() throws Exception {
    mvc.perform(post("/meals/quick").with(signedInAs(anna)).param("name", "Soup"))
        .andExpect(status().isForbidden());
  }
}
