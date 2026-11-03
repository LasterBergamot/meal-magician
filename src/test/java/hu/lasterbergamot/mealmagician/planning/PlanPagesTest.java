package hu.lasterbergamot.mealmagician.planning;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
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
 * The week page through the whole stack. Anna owns "Annas"; Bela owns "Belas" and must never see
 * Anna's plan (PLAN.md D8); the stranger is in no household. The planned week is a fixed one far
 * from today (Monday 2020-05-11, week 20), so the tests do not depend on the date they run. Texts
 * are the Hungarian ones, the app's default language.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PlanPagesTest {

  private static final LocalDate MONDAY = LocalDate.of(2020, 5, 11);
  private static final String WEEK = "2020-W20";

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
    return mvc.perform(request.with(signedInAs(anna)));
  }

  private MealView annasMeal(String name) {
    return meals.create(anna, annasHome, new MealDetails(name, null, null, List.of()));
  }

  @Test
  void showsTheWeekWithSevenDaysAndTheTable() throws Exception {
    String page =
        asAnna(get("/plan"))
            .andExpect(status().isOk())
            .andExpect(header().string("Vary", containsString("HX-Request")))
            .andExpect(content().string(containsString("Heti terv")))
            .andExpect(content().string(containsString("<table")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(page.split("<section id=\"day-", -1)).hasSize(8);
  }

  @Test
  void marksTodayInTheStripAndTheTableHeader() throws Exception {
    String page = asAnna(get("/plan")).andReturn().getResponse().getContentAsString();
    assertThat(page.split("aria-current=\"date\"", -1)).hasSize(3);
    assertThat(page)
        .containsPattern("data-tone=\"accent\"[^>]*>\\s*<svg[\\s\\S]*?</svg>\\s*Ma\\s*</span>");
    assertThat(page).doesNotContain("Vissza erre a hétre");
  }

  @Test
  void aWeekCodeShowsThatWeekWithNeighbourLinks() throws Exception {
    String page =
        asAnna(get("/plan").param("week", WEEK))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("20. hét")))
            .andExpect(content().string(containsString("href=\"/plan?week=2020-W19\"")))
            .andExpect(content().string(containsString("href=\"/plan?week=2020-W21\"")))
            .andExpect(content().string(containsString("Vissza erre a hétre")))
            .andExpect(content().string(not(containsString("aria-current=\"date\""))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(page).contains("id=\"day-2020-05-11\"").contains("id=\"day-2020-05-17\"");
  }

  @Test
  void anInvalidWeekShowsTheCurrentWeek() throws Exception {
    LocalDate today = LocalDate.now(clock);
    int number =
        today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    asAnna(get("/plan").param("week", "nonsense"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(number + ". hét")));
  }

  @Test
  void htmxGetsOnlyTheWeekFragment() throws Exception {
    asAnna(get("/plan").param("week", WEEK).header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("<html"))))
        .andExpect(content().string(containsString("id=\"plan-prev\"")));
  }

  @Test
  void plannedMealsShowInTheirDayAndSlotWithNoteAndArchivedBadge() throws Exception {
    MealView soup = annasMeal("Gulyásleves");
    MealView old = annasMeal("Régi rántotta");
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, soup.id(), "extra csipős");
    planning.add(anna, annasHome, MONDAY.plusDays(2), SlotType.BREAKFAST, old.id(), null);
    meals.archive(anna, annasHome, old.id());

    String page =
        asAnna(get("/plan").param("week", WEEK))
            .andExpect(content().string(containsString("Gulyásleves")))
            .andExpect(content().string(containsString("extra csipős")))
            .andExpect(content().string(containsString("Régi rántotta")))
            .andExpect(content().string(containsString("Archivált")))
            .andExpect(content().string(containsString("data-group=\"2020-05-11/LUNCH\"")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    int lunch = page.indexOf("data-group=\"2020-05-11/LUNCH\"");
    int nextGroup = page.indexOf("data-group=", lunch + 1);
    assertThat(page.substring(lunch, nextGroup)).contains("Gulyásleves");
  }

  private String weekPage() throws Exception {
    return asAnna(get("/plan").param("week", WEEK))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** The phone's day section split at its disclosure: what is up front, then what is behind it. */
  private static String[] phoneDay(String page, LocalDate day) {
    int start = page.indexOf("<section id=\"day-" + day + "\"");
    int more = page.indexOf("<details data-more=\"day-" + day + "\"", start);
    int end = page.indexOf("</section>", start);
    assertThat(more).as("the disclosure of %s", day).isPositive().isLessThan(end);
    return new String[] {page.substring(start, more), page.substring(more, end)};
  }

  @Test
  void anEmptyDayShowsOnlyLunchUpFrontAndTheOtherMealsBehindTheDisclosure() throws Exception {
    String[] parts = phoneDay(weekPage(), MONDAY);
    assertThat(parts[0]).contains("<li data-group=\"2020-05-11/LUNCH\"");
    assertThat(parts[0]).doesNotContain("<li data-group=\"2020-05-11/BREAKFAST\"");
    assertThat(parts[0]).doesNotContain("<li data-group=\"2020-05-11/DINNER\"");
    assertThat(parts[0]).doesNotContain("<li data-group=\"2020-05-11/SNACK\"");
    assertThat(parts[1]).contains("Más étkezések");
    assertThat(parts[1]).contains("<li data-group=\"2020-05-11/BREAKFAST\"");
    assertThat(parts[1]).contains("<li data-group=\"2020-05-11/DINNER\"");
    assertThat(parts[1]).contains("<li data-group=\"2020-05-11/SNACK\"");
    assertThat(parts[1]).contains("data-add=\"2020-05-11/DINNER\"");
    assertThat(parts[1]).doesNotContain("<li data-group=\"2020-05-11/LUNCH\"");
  }

  @Test
  void aDayWithABreakfastShowsLunchAndBreakfastUpFront() throws Exception {
    planning.add(anna, annasHome, MONDAY, SlotType.BREAKFAST, annasMeal("Rántotta").id(), null);

    String[] parts = phoneDay(weekPage(), MONDAY);
    assertThat(parts[0]).contains("<li data-group=\"2020-05-11/LUNCH\"");
    assertThat(parts[0]).contains("<li data-group=\"2020-05-11/BREAKFAST\"");
    assertThat(parts[1]).doesNotContain("<li data-group=\"2020-05-11/BREAKFAST\"");
    assertThat(parts[1]).contains("<li data-group=\"2020-05-11/DINNER\"");
    assertThat(parts[1]).contains("<li data-group=\"2020-05-11/SNACK\"");
  }

  @Test
  void aFullDayDrawsNoDisclosure() throws Exception {
    MealView meal = annasMeal("Mindenes");
    for (SlotType type : SlotType.values()) {
      planning.add(anna, annasHome, MONDAY, type, meal.id(), null);
    }

    String page = weekPage();
    assertThat(page).doesNotContain("<details data-more=\"day-2020-05-11\"");
    assertThat(page).contains("<details data-more=\"day-2020-05-12\"");
  }

  @Test
  void theTableShowsOnlyTheLunchRowOfAnEmptyWeekAndOffersShowAll() throws Exception {
    String page = weekPage();
    assertThat(page).contains("<tr data-row=\"LUNCH\" class=\"\">");
    for (String type : List.of("BREAKFAST", "DINNER", "SNACK")) {
      assertThat(page).contains("<tr data-row=\"" + type + "\" class=\"hidden ");
    }
    assertThat(page).contains("id=\"plan-show-all\"").contains("Minden étkezés");
  }

  @Test
  void theTableShowsTheRowOfATypeThatHoldsAMealWithoutTickingAnything() throws Exception {
    planning.add(
        anna, annasHome, MONDAY.plusDays(3), SlotType.DINNER, annasMeal("Pörkölt").id(), null);

    String page = weekPage();
    assertThat(page).contains("<tr data-row=\"DINNER\" class=\"\">");
    assertThat(page).contains("<tr data-row=\"SNACK\" class=\"hidden ");
  }

  @Test
  void theEnglishWeekNamesTheDisclosureAndTheCheckbox() throws Exception {
    String page =
        asAnna(get("/plan").param("week", WEEK).cookie(new Cookie("mm_lang", "en")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(page).contains("Other meals").contains("Show all meals");
  }

  @Test
  void anotherHouseholdsPlanNeverShows() throws Exception {
    MealView secret =
        meals.create(bela, belasHome, new MealDetails("Belas titok", null, null, List.of()));
    planning.add(bela, belasHome, MONDAY, SlotType.DINNER, secret.id(), "csak Bela");

    asAnna(get("/plan").param("week", WEEK))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Belas titok"))))
        .andExpect(content().string(not(containsString("csak Bela"))));
  }

  @Test
  void aPersonInNoHouseholdGetsNotFound() throws Exception {
    mvc.perform(get("/plan").with(signedInAs(stranger))).andExpect(status().isNotFound());
  }

  @Test
  void thePlanTabLinksToPlanAndIsCurrent() throws Exception {
    String page = asAnna(get("/plan")).andReturn().getResponse().getContentAsString();
    assertThat(page).containsPattern("href=\"/plan\"[^>]*aria-current=\"page\"");
  }

  @Test
  void theHtmxConfigReloadsOnAHistoryMiss() throws Exception {
    asAnna(get("/plan"))
        .andExpect(content().string(containsString("\"refreshOnHistoryMiss\":true")));
  }

  @Test
  void theEnglishCookieGivesEnglishHeadingsAndDates() throws Exception {
    asAnna(get("/plan").param("week", WEEK).cookie(new Cookie("mm_lang", "en")))
        .andExpect(content().string(containsString("Week 20")))
        .andExpect(content().string(containsString("May")));
  }
}
