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
 * The Tags field's suggestion list ({@code GET /meals/tags}) and the field on the meal form,
 * through the whole stack against the dev profile's H2. Anna owns "Annas", Bela owns "Belas", the
 * stranger is in no household. Texts are the Hungarian ones, the app's default language.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TagOptionsPageTest {

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long stranger;
  private long annasHome;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");
    annasHome = households.create(anna, "Annas").id();
    households.create(bela, "Belas");
  }

  private ResultActions options(long userId, String q) throws Exception {
    return mvc.perform(get("/meals/tags").param("q", q).with(signedInAs(userId)));
  }

  private void meal(long user, long home, String name, String... tags) {
    meals.create(user, home, new MealDetails(name, null, null, List.of(tags)));
  }

  // ---- the list ----

  @Test
  void theRowsAreOptionsWithTheExactSpellingAndNoPageAround() throws Exception {
    meal(anna, annasHome, "Stew", "Vegán", "quick");

    options(anna, "vegan")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("role=\"option\"")))
        .andExpect(content().string(containsString("data-value=\"Vegán\"")))
        .andExpect(content().string(not(containsString("quick"))))
        .andExpect(content().string(not(containsString("<html"))))
        .andExpect(content().string(not(containsString("data-add"))));
  }

  @Test
  void theMostUsedTagIsTheFirstRow() throws Exception {
    meal(anna, annasHome, "Stew", "soup", "vegan");
    meal(anna, annasHome, "Curry", "vegan");

    String html = options(anna, "").andReturn().getResponse().getContentAsString();

    assertThat(html.indexOf("data-value=\"vegan\"")).isPositive();
    assertThat(html.indexOf("data-value=\"vegan\""))
        .isLessThan(html.indexOf("data-value=\"soup\""));
  }

  @Test
  void aNameWithNoMatchGivesAnEmptyBodySoTheListHidesItself() throws Exception {
    meal(anna, annasHome, "Stew", "vegan");

    String html = options(anna, "zzzz").andReturn().getResponse().getContentAsString();

    assertThat(html).doesNotContain("<li");
  }

  @Test
  void anotherHouseholdsTagsAreNeverListed() throws Exception {
    meal(anna, annasHome, "Stew", "titkos");

    options(bela, "titkos")
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("titkos"))));
    options(bela, "")
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("titkos"))));
  }

  @Test
  void aPersonInNoHouseholdGetsNotFound() throws Exception {
    options(stranger, "v").andExpect(status().isNotFound());
  }

  @Test
  void aMissingTextListsTheMostUsedTags() throws Exception {
    meal(anna, annasHome, "Stew", "vegan");

    mvc.perform(get("/meals/tags").with(signedInAs(anna)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-value=\"vegan\"")));
  }

  // ---- the field on the form ----

  @Test
  void theTagsFieldStaysATextInputWhoseComboboxControlsPointAtAnExistingListbox() throws Exception {
    String html =
        mvc.perform(get("/meals/new").with(signedInAs(anna)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Matcher input = Pattern.compile("<input id=\"meal-tags\"[^>]*>").matcher(html);
    assertThat(input.find()).isTrue();
    assertThat(input.group())
        .contains("name=\"tags\"")
        .contains("type=\"text\"")
        .contains("role=\"combobox\"")
        .contains("aria-autocomplete=\"list\"")
        .contains("aria-expanded=\"false\"")
        .contains("aria-controls=\"meal-tags-listbox\"")
        .contains("aria-describedby=\"meal-tags-hint\"")
        .contains("hx-get=\"/meals/tags\"")
        .contains("hx-target=\"#meal-tags-listbox\"");
    assertThat(html)
        .contains("id=\"meal-tags-listbox\"")
        .contains("role=\"listbox\"")
        .contains("aria-label=\"Címkejavaslatok\"")
        .contains("x-data=\"tagSuggest\"")
        .contains("<label for=\"meal-tags\"");
  }

  @Test
  void theEditFormKeepsTheCommaSeparatedValueInTheField() throws Exception {
    MealView stew =
        meals.create(
            anna, annasHome, new MealDetails("Stew", null, null, List.of("vegan", "quick")));

    String html =
        mvc.perform(get("/meals/" + stew.id() + "/edit").with(signedInAs(anna)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("name=\"tags\" type=\"text\" value=\"quick, vegan\"");
  }

  @Test
  void aTagTypedWithOtherCaseStillSavesAsTheExistingTag() throws Exception {
    meal(anna, annasHome, "Stew", "Vegan");
    meal(anna, annasHome, "Curry", "VEGAN");

    assertThat(meals.tagNames(anna, annasHome)).containsExactly("Vegan");
  }
}
