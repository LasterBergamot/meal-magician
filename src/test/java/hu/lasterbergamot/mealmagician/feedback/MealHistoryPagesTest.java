package hu.lasterbergamot.mealmagician.feedback;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
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
 * The "How it went" block of a meal's page, through the whole stack (#132, D27). Anna owns "Annas"
 * with Cili and Dimi as plain members; Bela owns "Belas" and must never see Anna's meal (PLAN.md
 * D8). The page is loaded by htmx from {@code /feedback/meals/{id}}, and the meal page carries the
 * container that asks for it. Texts are the Hungarian ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MealHistoryPagesTest {

  private static final Locale HUNGARIAN = Locale.forLanguageTag("hu");

  @Autowired private MockMvc mvc;
  @Autowired private HouseholdService households;
  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private RatingService ratings;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long cili;
  private long dimi;
  private long annasHome;
  private long mealId;
  private LocalDate today;
  private SlotView yesterdaysLunch;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    dimi = HouseholdTestSupport.insertUser(jdbc, "dimi");
    annasHome = households.create(anna, "Annas").id();
    households.create(bela, "Belas");
    for (long member : new long[] {cili, dimi}) {
      jdbc.update(
          "insert into membership (household_id, user_id, role, joined_at)"
              + " values (?, ?, 'MEMBER', now())",
          annasHome,
          member);
    }
    mealId = meals.quickAdd(anna, annasHome, "Gulyásleves").id();
    today = LocalDate.now(clock);
    yesterdaysLunch =
        planning.add(anna, annasHome, today.minusDays(1), SlotType.LUNCH, mealId, null);
  }

  private ResultActions htmx(long userId, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header("HX-Request", "true").with(signedInAs(userId)).with(csrf()));
  }

  private ResultActions as(long userId, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(signedInAs(userId)).with(csrf()));
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private String historyUrl() {
    return "/feedback/meals/" + mealId;
  }

  private void rate(long userId, SlotView slot, Thumb thumb, Integer stars, String comment) {
    ratings.give(userId, annasHome, slot.id(), new RatingInput(thumb, stars, comment));
  }

  // ---- the lazy container on the meal page ----

  @Test
  void theMealPageCarriesTheLazyContainerThatLoadsTheHistory() throws Exception {
    String page = body(as(anna, get("/meals/" + mealId)).andExpect(status().isOk()));

    assertThat(page)
        .contains("id=\"meal-history\"")
        .contains("hx-get=\"/feedback/meals/" + mealId + "\"")
        .contains("hx-trigger=\"load\"")
        .contains("hx-target=\"#meal-history-body\"")
        .contains("Hogy sikerült");
  }

  // ---- the block itself ----

  @Test
  void theHistoryCountsTheThumbsUpAndDown() throws Exception {
    rate(anna, yesterdaysLunch, Thumb.UP, null, null);
    rate(cili, yesterdaysLunch, Thumb.UP, null, null);
    rate(dimi, yesterdaysLunch, Thumb.DOWN, null, null);

    String block = body(htmx(anna, get(historyUrl())).andExpect(status().isOk()));

    assertThat(block).contains("<span data-count=\"up\">2</span>");
    assertThat(block).contains("<span data-count=\"down\">1</span>");
    assertThat(block).doesNotContain("<html");
  }

  @Test
  void theHistoryListsRatingsByDisplayNameWithTheirDateAndCommentAndNeverAnEmail()
      throws Exception {
    rate(anna, yesterdaysLunch, Thumb.UP, 4, "Finom volt");
    rate(dimi, yesterdaysLunch, Thumb.DOWN, null, "Túl sós");
    String dateLabel =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(HUNGARIAN)
            .format(yesterdaysLunch.date());

    String block = body(htmx(anna, get(historyUrl())).andExpect(status().isOk()));

    assertThat(block)
        .contains("data-rating-author=\"anna\"")
        .contains("data-rating-author=\"dimi\"")
        .contains("Finom volt")
        .contains("Túl sós")
        .contains(dateLabel)
        .doesNotContain("@example.com");
  }

  @Test
  void theHistoryShowsTheAverageOfTheStarsToOneDecimal() throws Exception {
    rate(anna, yesterdaysLunch, Thumb.UP, 4, null);
    rate(cili, yesterdaysLunch, Thumb.UP, 5, null);

    htmx(anna, get(historyUrl()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<span data-average>4,5</span>")));
  }

  @Test
  void theAverageIsLeftOutWhenNobodyGaveStars() throws Exception {
    rate(anna, yesterdaysLunch, Thumb.UP, null, null);

    htmx(anna, get(historyUrl()))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("data-average"))));
  }

  @Test
  void aMealWithNoThumbsShowsOnlyTheEmptyLine() throws Exception {
    htmx(anna, get(historyUrl()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Ezt az ételt még senki sem értékelte.")))
        .andExpect(content().string(not(containsString("data-count"))));
  }

  @Test
  void ratingsOfARemovedSlotStillCountAndStillShow() throws Exception {
    rate(anna, yesterdaysLunch, Thumb.UP, null, "Maradt a listán");
    jdbc.update("delete from meal_slot");

    htmx(anna, get(historyUrl()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<span data-count=\"up\">1</span>")))
        .andExpect(content().string(containsString("Maradt a listán")));
  }

  @Test
  void theHistoryListsOnlyTheLatestTenRatings() throws Exception {
    for (int daysBack = 1; daysBack <= 11; daysBack++) {
      SlotView slot =
          daysBack == 1
              ? yesterdaysLunch
              : planning.add(
                  anna, annasHome, today.minusDays(daysBack), SlotType.LUNCH, mealId, null);
      rate(anna, slot, Thumb.UP, null, null);
    }

    String block = body(htmx(anna, get(historyUrl())).andExpect(status().isOk()));

    assertThat(block.split("data-rating-author=", -1)).hasSize(11);
    assertThat(block).contains("<span data-count=\"up\">11</span>");
  }

  @Test
  void aMealOfAnotherHouseholdIsNotFound() throws Exception {
    htmx(bela, get(historyUrl())).andExpect(status().isNotFound());
  }
}
