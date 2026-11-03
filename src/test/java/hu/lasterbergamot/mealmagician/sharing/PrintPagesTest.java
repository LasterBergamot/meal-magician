package hu.lasterbergamot.mealmagician.sharing;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
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
 * The members' print page through the whole stack (PLAN.md D29): what it shows, that it is a bare
 * light page, that notes appear only here, and that another household's data never does. Anna owns
 * the household with the printed week; Cili owns another one. Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PrintPagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private OccasionService occasions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long cili;

  @BeforeEach
  void aPlannedWeek() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    long annas = households.create(anna, "Annas").id();
    long cilis = households.create(cili, "Cilis").id();
    long stew = meals.quickAdd(anna, annas, "Goulash stew").id();
    long soup = meals.quickAdd(anna, annas, "Fish soup").id();
    long foreign = meals.quickAdd(cili, cilis, "Foreign dish").id();
    planning.add(anna, annas, MONDAY, SlotType.LUNCH, stew, "Less salt please");
    planning.add(anna, annas, MONDAY, SlotType.DINNER, soup, null);
    planning.add(cili, cilis, MONDAY, SlotType.LUNCH, foreign, "Foreign note");
    occasions.create(
        anna,
        annas,
        new OccasionDetails("Grandma party", MONDAY.plusDays(4), OccasionType.PARTY, null));
  }

  private ResultActions print(String query, long userId) throws Exception {
    return mvc.perform(get("/plan/print" + query).with(signedInAs(userId)));
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  void printsTheWeeksMealsNotesAndOccasionsOnALightPage() throws Exception {
    ResultActions result = print("?week=2026-W45", anna).andExpect(status().isOk());

    String html = body(result);
    assertThat(html)
        .contains("data-theme=\"light\"")
        .contains("45. hét")
        .contains("Goulash stew")
        .contains("Less salt please")
        .contains("Fish soup")
        .contains("Grandma party")
        .contains("Ebéd")
        .contains("Vacsora");
  }

  @Test
  void theNotesAppearOnlyBesideTheMealTheyBelongTo() throws Exception {
    String html = body(print("?week=2026-W45", anna));

    assertThat(html.indexOf("Less salt please")).isGreaterThan(html.indexOf("Goulash stew"));
    assertThat(html.indexOf("Less salt please")).isLessThan(html.indexOf("Fish soup"));
  }

  @Test
  void listsOnlyTheMealTypesThatHoldAMeal() throws Exception {
    String html = body(print("?week=2026-W45", anna));

    assertThat(html).doesNotContain("Reggeli").doesNotContain("Uzsonna");
  }

  @Test
  void hasNoShellAndNoFooterAboutLinks() throws Exception {
    print("?week=2026-W45", anna)
        .andExpect(content().string(not(containsString("<nav"))))
        .andExpect(content().string(not(containsString("tabbar"))))
        .andExpect(content().string(not(containsString("A link birtokában"))))
        .andExpect(content().string(containsString("href=\"/plan?week=2026-W45\"")));
  }

  @Test
  void showsNothingFromAnotherHousehold() throws Exception {
    String html = body(print("?week=2026-W45", anna));

    assertThat(html).doesNotContain("Foreign dish").doesNotContain("Foreign note");
  }

  @Test
  void aBadWeekCodeFallsBackToTheCurrentWeek() throws Exception {
    LocalDate today = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    String expected = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + ". hét";

    assertThat(body(print("?week=bad", anna))).contains(expected);
    assertThat(body(print("", anna))).contains(expected);
  }

  @Test
  void aWeekThatTheYearDoesNotHaveFallsBackToo() throws Exception {
    LocalDate today = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    String expected = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + ". hét";

    assertThat(body(print("?week=2021-W53", anna))).contains(expected);
    assertThat(body(print("?week=2026-W00", anna))).contains(expected);
  }

  @Test
  void aPersonInNoHouseholdGetsNotFound() throws Exception {
    long stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");

    print("?week=2026-W45", stranger).andExpect(status().isNotFound());
  }
}
