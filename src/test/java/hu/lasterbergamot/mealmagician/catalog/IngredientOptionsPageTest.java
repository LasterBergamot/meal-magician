package hu.lasterbergamot.mealmagician.catalog;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.util.List;
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
 * The ingredient picker's list ({@code GET /meals/ingredients}) and the combobox on the meal form,
 * through the whole stack against the dev profile's H2. Anna owns "Annas", Bela owns "Belas", the
 * stranger is in no household. Texts are the Hungarian ones, the app's default language.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class IngredientOptionsPageTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private JdbcTemplate jdbc;

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

  private ResultActions options(long userId, String q) throws Exception {
    return mvc.perform(get("/meals/ingredients").param("q", q).with(signedInAs(userId)));
  }

  // ---- the list ----

  @Test
  void typingWithoutAccentsFindsTheAccentedStarterName() throws Exception {
    options(anna, "tojas")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-value=\"tojás\"")))
        .andExpect(content().string(containsString("role=\"option\"")))
        .andExpect(content().string(not(containsString("<html"))));
  }

  @Test
  void aNameThatIsNotListedGetsOnlyTheAddRow() throws Exception {
    options(anna, "zzzz")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-value=\"zzzz\" data-add")))
        .andExpect(content().string(containsString("«zzzz» hozzáadása")));
    String html = options(anna, "zzzz").andReturn().getResponse().getContentAsString();
    assertThat(count(html, "role=\"option\"")).isEqualTo(1);
  }

  @Test
  void aNameThatMatchesExistingOneInAnotherCaseGetsNoAddRow() throws Exception {
    options(anna, "TOJÁS")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-value=\"tojás\"")))
        .andExpect(content().string(not(containsString("data-add"))));
  }

  @Test
  void theHouseholdsOwnIngredientsComeBeforeTheStarterList() throws Exception {
    meals.create(
        anna,
        annasHome,
        new MealDetails("Stew", null, null, List.of(), null, List.of("Zöldségkocka")));

    String html = options(anna, "").andReturn().getResponse().getContentAsString();

    assertThat(html).contains("data-value=\"Zöldségkocka\"");
    // It is the first row, ahead of every starter name.
    assertThat(html.indexOf("data-value=")).isEqualTo(html.indexOf("data-value=\"Zöldségkocka\""));
    assertThat(count(html, "data-value=")).isGreaterThan(1);
    assertThat(html).doesNotContain("data-add");
  }

  @Test
  void anotherHouseholdsIngredientsAreNeverListed() throws Exception {
    meals.create(
        anna, annasHome, new MealDetails("Stew", null, null, List.of(), null, List.of("Titkossó")));

    options(bela, "titkos")
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("data-value=\"Titkossó\""))))
        .andExpect(content().string(containsString("data-add")));
  }

  @Test
  void aPersonInNoHouseholdGetsNotFound() throws Exception {
    options(stranger, "tojas").andExpect(status().isNotFound());
  }

  @Test
  void aListWithNothingToOfferSaysSo() throws Exception {
    jdbc.update("delete from ingredient_seed");

    options(anna, "")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Nincs több javaslat")));
  }

  // ---- the combobox on the form ----

  @Test
  void theFormHasAComboboxWhoseControlsPointAtAnExistingListbox() throws Exception {
    String html =
        mvc.perform(get("/meals/new").with(signedInAs(anna)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("role=\"combobox\"").contains("aria-autocomplete=\"list\"");
    Matcher controls = Pattern.compile("aria-controls=\"([^\"]+)\"").matcher(html);
    assertThat(controls.find()).isTrue();
    assertThat(html).contains("id=\"" + controls.group(1) + "\"").contains("role=\"listbox\"");
    assertThat(html).contains("hx-get=\"/meals/ingredients\"").contains("x-data=\"combobox\"");
  }

  @Test
  void theEditFormDrawsAChipWithAHiddenInputPerIngredient() throws Exception {
    MealView stew =
        meals.create(
            anna,
            annasHome,
            new MealDetails("Stew", null, null, List.of(), null, List.of("Salt, coarse", "Egg")));

    String html =
        mvc.perform(get("/meals/" + stew.id() + "/edit").with(signedInAs(anna)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html)
        .contains("name=\"ingredients\" value=\"Salt, coarse\"")
        .contains("name=\"ingredients\" value=\"Egg\"")
        .contains("aria-label=\"Salt, coarse eltávolítása\"");
  }

  private static int count(String html, String text) {
    int found = 0;
    for (int at = html.indexOf(text); at >= 0; at = html.indexOf(text, at + 1)) {
      found++;
    }
    return found;
  }
}
