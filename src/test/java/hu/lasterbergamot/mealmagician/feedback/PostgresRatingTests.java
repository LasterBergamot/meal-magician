package hu.lasterbergamot.mealmagician.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ratings on the real database: the checks and foreign keys of V10 must refuse bad rows, a rating
 * must outlive its slot, and two tabs giving at once must end with one row and no exception. Not
 * {@code @Transactional}, because the race needs separate transactions; the data is cleaned up by
 * hand instead.
 */
@Tag("integration")
class PostgresRatingTests extends AbstractPostgresIntegrationTest {

  @Autowired private RatingService ratings;
  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long stew;
  private long belasPie;
  private long slotId;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "pg-rating-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "pg-rating-bela");
    annasHome = households.create(anna, "Pg rating anna").id();
    belasHome = households.create(bela, "Pg rating bela").id();
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
    belasPie = meals.quickAdd(bela, belasHome, "Pie").id();
    slotId = planning.add(anna, annasHome, LocalDate.now(clock), SlotType.LUNCH, stew, null).id();
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from rating where household_id in (?, ?)", annasHome, belasHome);
    jdbc.update("delete from meal_slot where household_id in (?, ?)", annasHome, belasHome);
    jdbc.update("delete from household where id in (?, ?)", annasHome, belasHome);
    jdbc.update("delete from app_user where id in (?, ?)", anna, bela);
  }

  private int ratingRows() {
    return jdbc.queryForObject("select count(*) from rating", Integer.class);
  }

  private void insertRaw(long householdId, long mealId, String thumb, Integer stars, long userId) {
    jdbc.update(
        "insert into rating (household_id, slot_id, meal_id, slot_date, slot_type, user_id, thumb,"
            + " stars, created_at, updated_at) values (?, ?, ?, current_date, 'LUNCH', ?, ?, ?,"
            + " now(), now())",
        householdId,
        slotId + 1000,
        mealId,
        userId,
        thumb,
        stars);
  }

  @Test
  void twoConcurrentGivesForTheSameSlotAndMemberEndWithOneRowAndNoException() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      Callable<RatingView> give =
          () -> {
            start.await();
            return ratings.give(anna, annasHome, slotId, new RatingInput(Thumb.UP, 5, null));
          };
      Future<RatingView> first = pool.submit(give);
      Future<RatingView> second = pool.submit(give);
      start.countDown();

      RatingView a = first.get();
      RatingView b = second.get();

      assertThat(ratingRows()).isEqualTo(1);
      assertThat(a.id()).isEqualTo(b.id());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void givingAndClearingWorkOnPostgres() {
    ratings.give(anna, annasHome, slotId, new RatingInput(Thumb.DOWN, null, "salty"));
    ratings.give(anna, annasHome, slotId, new RatingInput(Thumb.UP, 4, null));

    assertThat(ratings.forSlot(anna, annasHome, slotId))
        .singleElement()
        .satisfies(r -> assertThat(r.thumb()).isEqualTo(Thumb.UP));
    assertThat(ratings.forMeal(anna, annasHome, stew, 5).averageStars()).isEqualTo(4.0);
    assertThat(ratings.clear(anna, annasHome, slotId)).isTrue();
    assertThat(ratingRows()).isZero();
  }

  @Test
  void aRatingOutlivesItsSlot() {
    ratings.give(anna, annasHome, slotId, new RatingInput(Thumb.UP, null, null));

    jdbc.update("delete from meal_slot where id = ?", slotId);

    assertThat(ratings.forMeal(anna, annasHome, stew, 5).latest()).hasSize(1);
  }

  @Test
  void aRatedMealCannotBeDeleted() {
    ratings.give(anna, annasHome, slotId, new RatingInput(Thumb.UP, null, null));
    jdbc.update("delete from meal_slot where id = ?", slotId);

    assertThatThrownBy(() -> jdbc.update("delete from meal where id = ?", stew))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aRatingCannotPointAtAnotherHouseholdsMeal() {
    assertThatThrownBy(() -> insertRaw(annasHome, belasPie, "UP", null, anna))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aThumbOtherThanUpOrDownIsRefused() {
    assertThatThrownBy(() -> insertRaw(annasHome, stew, "MEH", null, anna))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void starsOutsideOneToFiveAreRefused() {
    assertThatThrownBy(() -> insertRaw(annasHome, stew, "UP", 6, anna))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingTheHouseholdDeletesItsRatings() {
    ratings.give(anna, annasHome, slotId, new RatingInput(Thumb.UP, null, null));

    jdbc.update("delete from household where id = ?", annasHome);

    assertThat(ratingRows()).isZero();
  }
}
