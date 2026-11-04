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
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
import java.time.LocalDate;
import java.util.List;
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
 * The occasion menu suggestions (#161, Appendix D.5) through the whole stack with the scripted
 * model. Anna owns the birthday "Annas"; Bela's household must get 404 for every route on it. The
 * test checks that a suggestion never changes the menu, that an invalid meal id is dropped, and
 * that the earlier menus of the same kind (and only those) reach the prompt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "fake-ai"})
class AssistantOccasionPagesTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private OccasionService occasions;
  @Autowired private UsageLedger ledger;
  @Autowired private ScriptedChatModel model;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long birthday;
  private long gulyas;

  @BeforeEach
  void people() {
    model.reset();
    anna = HouseholdTestSupport.insertUser(jdbc, "menu-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "menu-bela");
    annasHome = households.create(anna, "Menu Annas").id();
    belasHome = households.create(bela, "Menu Belas").id();
    gulyas = meal(anna, annasHome, "Gulyás").id();
    long lastYear =
        occasions
            .create(
                anna,
                annasHome,
                new OccasionDetails(
                    "Tavalyi buli", LocalDate.of(2025, 5, 1), OccasionType.BIRTHDAY, null))
            .id();
    occasions.addItem(anna, annasHome, lastYear, gulyas);
    long oldHoliday =
        occasions
            .create(
                anna,
                annasHome,
                new OccasionDetails(
                    "Karácsony", LocalDate.of(2024, 12, 24), OccasionType.HOLIDAY, null))
            .id();
    occasions.addItem(anna, annasHome, oldHoliday, meal(anna, annasHome, "Töltött káposzta").id());
    long archived = meal(anna, annasHome, "Régi étel").id();
    long lastBirthday =
        occasions
            .create(
                anna,
                annasHome,
                new OccasionDetails(
                    "Két éve", LocalDate.of(2024, 5, 1), OccasionType.BIRTHDAY, null))
            .id();
    occasions.addItem(anna, annasHome, lastBirthday, archived);
    meals.archive(anna, annasHome, archived);
    birthday =
        occasions
            .create(
                anna,
                annasHome,
                new OccasionDetails("Annas", LocalDate.of(2026, 5, 1), OccasionType.BIRTHDAY, null))
            .id();
  }

  @AfterEach
  void cleanUp() {
    for (long home : new long[] {annasHome, belasHome}) {
      jdbc.update(
          "delete from occasion_item where occasion_id in (select id from occasion where household_id = ?)",
          home);
      jdbc.update("delete from occasion where household_id = ?", home);
      jdbc.update("delete from meal where household_id = ?", home);
      jdbc.update("delete from assistant_usage where household_id = ?", home);
      jdbc.update("delete from household where id = ?", home);
    }
    jdbc.update("delete from app_user where id in (?, ?)", anna, bela);
  }

  private MealView meal(long user, long home, String name) {
    return meals.create(user, home, new MealDetails(name, null, null, List.of()));
  }

  private ResultActions as(long user, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(user)).with(csrf()).header("HX-Request", "true"));
  }

  private ResultActions suggest(long user, long occasionId) throws Exception {
    return as(user, post("/assistant/occasions/" + occasionId + "/menu"));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private OccasionView occasion(long occasionId) {
    return occasions.find(anna, annasHome, occasionId);
  }

  @Test
  void theOccasionPageLoadsTheSuggestionLoader() throws Exception {
    String page =
        body(
            mvc.perform(get("/occasions/" + birthday).with(signedInAs(anna)))
                .andExpect(status().isOk()));

    assertThat(page)
        .contains("hx-get=\"/assistant/occasions/" + birthday + "/entry\"")
        .contains("hx-trigger=\"load\"");
  }

  @Test
  void theLoaderShowsTheButtonAndNoSuggestionsYet() throws Exception {
    String entry =
        body(
            as(anna, get("/assistant/occasions/" + birthday + "/entry"))
                .andExpect(status().isOk()));

    assertThat(entry)
        .contains("Menüjavaslat")
        .contains("occasion-suggest-menu")
        .doesNotContain("Gulyás");
    assertThat(model.requests()).isEmpty();
  }

  @Test
  void suggestionsAreLibraryRowsAndDashedIdeasAndUseOnlyTheEarlierMenusOfTheSameKind()
      throws Exception {
    String menu = body(suggest(anna, birthday).andExpect(status().isOk()));

    assertThat(menu)
        .contains("value=\"" + gulyas + "\"")
        .contains("Gulyás")
        .contains("Tökkrémleves")
        .contains("action=\"/occasions/" + birthday + "/menu\"")
        .contains("hx-post=\"/assistant/occasions/" + birthday + "/ideas\"");
    assertThat(model.prompts().getFirst())
        .contains("BIRTHDAY")
        .contains("Tavalyi buli")
        .doesNotContain("Töltött káposzta")
        .doesNotContain("Régi étel");
    assertThat(occasion(birthday).menu()).isEmpty();
  }

  @Test
  void anInvalidMealIdFromTheModelIsDroppedAndNeverRendered() throws Exception {
    model.setMenuInvalidId(true);

    String menu = body(suggest(anna, birthday).andExpect(status().isOk()));

    assertThat(menu)
        .doesNotContain("value=\"" + ScriptedChatModel.INVALID_ID + "\"")
        .contains("Gulyás");
  }

  @Test
  void anAddedLibrarySuggestionGoesThroughThePlannersMenuRoute() throws Exception {
    suggest(anna, birthday).andExpect(status().isOk());

    as(anna, post("/occasions/" + birthday + "/menu").param("mealId", String.valueOf(gulyas)))
        .andExpect(status().isOk());

    assertThat(occasion(birthday).menu()).hasSize(1);
  }

  @Test
  void anIdeaIsAddedToTheLibraryButNotToTheMenu() throws Exception {
    String added =
        body(
            as(
                    anna,
                    post("/assistant/occasions/" + birthday + "/ideas")
                        .param("name", "Tökkrémleves")
                        .param("tag", "leves")
                        .param("ingredient", "tök"))
                .andExpect(status().isOk()));

    assertThat(added).contains("Felvéve az ételek közé");
    assertThat(meals.search(anna, annasHome, new MealSearch("Tökkrémleves", null, null, false)))
        .hasSize(1);
    assertThat(occasion(birthday).menu()).isEmpty();
  }

  @Test
  void anIdeaWithoutAUsableNameIsABadRequest() throws Exception {
    as(anna, post("/assistant/occasions/" + birthday + "/ideas").param("name", "   "))
        .andExpect(status().isBadRequest());
    as(anna, post("/assistant/occasions/" + birthday + "/ideas"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anIdeaWithoutTagsOrIngredientsIsAddedAsItIs() throws Exception {
    as(anna, post("/assistant/occasions/" + birthday + "/ideas").param("name", "Lecsó"))
        .andExpect(status().isOk());

    assertThat(meals.search(anna, annasHome, new MealSearch("Lecsó", null, null, false)))
        .hasSize(1);
  }

  @Test
  void anotherHouseholdsOccasionIsNotFoundOnEveryRoute() throws Exception {
    as(bela, get("/assistant/occasions/" + birthday + "/entry")).andExpect(status().isNotFound());
    suggest(bela, birthday).andExpect(status().isNotFound());
    as(bela, post("/assistant/occasions/" + birthday + "/ideas").param("name", "Lecsó"))
        .andExpect(status().isNotFound());
    assertThat(model.requests()).isEmpty();
  }

  @Test
  void aBadAnswerIsAnUnavailableAlertWithTheRetryButton() throws Exception {
    model.setMenuReply("not json at all");

    String failed = body(suggest(anna, birthday).andExpect(status().isServiceUnavailable()));

    assertThat(failed).contains("A varázsló most nem érhető el.").contains("Újra");
  }

  @Test
  void theSpendCapStopsTheMenuCallWithItsOwnMessage() throws Exception {
    ledger.record(annasHome, 10_000_000, 10_000_000);

    String capped = body(suggest(anna, birthday).andExpect(status().isTooManyRequests()));

    assertThat(capped).contains("Elfogyott a varázsló e havi kerete.");
    assertThat(model.requests()).isEmpty();
  }

  @Test
  void anEmptyAnswerSaysThereIsNoGoodIdea() throws Exception {
    model.setMenuReply("{\"mealIds\":[],\"ideas\":[]}");

    String menu = body(suggest(anna, birthday).andExpect(status().isOk()));

    assertThat(menu).contains("Most nincs jó ötletem erre.");
  }

  @Test
  void anIdeaWithoutANameIsLeftOutOfTheSuggestions() throws Exception {
    model.setMenuReply(
        "{\"mealIds\":[],\"ideas\":[{\"name\":\"   \",\"reason\":\"x\",\"tags\":[],\"ingredients\":[]}]}");

    String menu = body(suggest(anna, birthday).andExpect(status().isOk()));

    assertThat(menu).contains("Most nincs jó ötletem erre.").doesNotContain("data-idea");
  }
}
