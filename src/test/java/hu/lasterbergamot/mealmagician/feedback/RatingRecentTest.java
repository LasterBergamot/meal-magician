package hu.lasterbergamot.mealmagician.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code RatingService.recent}: the latest ratings across a household's meals, against the dev
 * profile's H2. Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RatingRecentTest {

  @Autowired private RatingService ratings;
  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long cili;
  private long annasHome;
  private long belasHome;
  private long stew;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "rr-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "rr-bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "rr-cili");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at)"
            + " values (?, ?, 'MEMBER', now())",
        annasHome,
        cili);
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
  }

  private SlotView slot(int daysAgo, SlotType type) {
    return planning.add(anna, annasHome, LocalDate.now(clock).minusDays(daysAgo), type, stew, null);
  }

  private void rate(long user, long home, SlotView slot, Thumb thumb, String comment) {
    ratings.give(user, home, slot.id(), new RatingInput(thumb, null, comment));
  }

  @Test
  void listsTheLatestRatingsOfAnyMealNewestFirstWithComments() {
    SlotView first = slot(3, SlotType.LUNCH);
    SlotView second = slot(2, SlotType.DINNER);
    rate(anna, annasHome, first, Thumb.UP, "lovely");
    rate(cili, annasHome, second, Thumb.DOWN, "too salty");
    jdbc.update(
        "update rating set updated_at = updated_at - interval '1' hour where user_id = ?", anna);

    List<RatingView> recent = ratings.recent(anna, annasHome, 10);

    assertThat(recent).extracting(RatingView::comment).containsExactly("too salty", "lovely");
    assertThat(recent).extracting(RatingView::userId).containsExactly(cili, anna);
  }

  @Test
  void aLimitAboveOneHundredIsClamped() {
    for (int i = 1; i <= 105; i++) {
      jdbc.update(
          "insert into rating (household_id, slot_id, meal_id, slot_date, slot_type, user_id,"
              + " thumb, created_at, updated_at) values (?, ?, ?, ?, 'LUNCH', ?, 'UP', now(), now())",
          annasHome,
          1_000_000L + i,
          stew,
          LocalDate.now(clock).minusDays(1),
          anna);
    }

    assertThat(ratings.recent(anna, annasHome, 500)).hasSize(100);
    assertThat(ratings.recent(anna, annasHome, 7)).hasSize(7);
    assertThat(ratings.recent(anna, annasHome, 0)).hasSize(1);
    assertThat(ratings.recent(anna, annasHome, -5)).hasSize(1);
  }

  @Test
  void aFormerMemberIsDropped() {
    rate(anna, annasHome, slot(2, SlotType.LUNCH), Thumb.UP, "mine");
    rate(cili, annasHome, slot(1, SlotType.DINNER), Thumb.UP, "left");
    jdbc.update("delete from membership where household_id = ? and user_id = ?", annasHome, cili);

    assertThat(ratings.recent(anna, annasHome, 10))
        .extracting(RatingView::comment)
        .containsExactly("mine");
  }

  @Test
  void anotherHouseholdsRatingsNeverAppear() {
    long theirMeal = meals.quickAdd(bela, belasHome, "Theirs").id();
    SlotView theirSlot =
        planning.add(
            bela, belasHome, LocalDate.now(clock).minusDays(1), SlotType.LUNCH, theirMeal, null);
    rate(bela, belasHome, theirSlot, Thumb.UP, "secret");

    assertThat(ratings.recent(anna, annasHome, 10)).isEmpty();
    assertThat(ratings.recent(bela, belasHome, 10)).hasSize(1);
  }

  @Test
  void aNonMemberGetsNotFound() {
    assertThatThrownBy(() -> ratings.recent(bela, annasHome, 10))
        .isInstanceOf(HouseholdNotFoundException.class);
  }
}
