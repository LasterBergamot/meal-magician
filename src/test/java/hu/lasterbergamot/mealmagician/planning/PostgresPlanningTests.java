package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The weekly plan on the real database. The composite foreign key must refuse a cross-household
 * meal on Postgres too, a planned meal must not be deletable, the week's date range and the
 * recently-used aggregate must run on Postgres, and a version clash must surface as a conflict
 * there. Each test is rolled back.
 */
@Transactional
@Tag("integration")
class PostgresPlanningTests extends AbstractPostgresIntegrationTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  @Test
  void theForeignKeyRefusesACrossHouseholdMealOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-plan-anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-plan-bela");
    long annasHome = households.create(anna, "Pg plan anna").id();
    long belasHome = households.create(bela, "Pg plan bela").id();
    long belasPie = meals.quickAdd(bela, belasHome, "Pie").id();

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into meal_slot (household_id, date, slot_type, position, meal_id, version)"
                    + " values (?, ?, 'LUNCH', 0, ?, 0)",
                annasHome,
                MONDAY,
                belasPie))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aPlannedMealCannotBeDeletedButAnArchivedOneStillCanBeKept() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-del-anna");
    long home = households.create(anna, "Pg del").id();
    long stew = meals.quickAdd(anna, home, "Stew").id();
    planning.add(anna, home, MONDAY, SlotType.DINNER, stew, null);
    meals.archive(anna, home, stew);

    assertThat(planning.week(anna, home, MONDAY).slots()).hasSize(1);
    // Last on purpose: a refused statement aborts the Postgres transaction, so nothing may follow
    // it.
    assertThatThrownBy(() -> jdbc.update("delete from meal where id = ?", stew))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingAHouseholdRemovesItsPlanAndItsMeals() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-cascade-anna");
    long home = households.create(anna, "Pg cascade").id();
    long stew = meals.quickAdd(anna, home, "Stew").id();
    planning.add(anna, home, MONDAY, SlotType.DINNER, stew, null);

    jdbc.update("delete from household where id = ?", home);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from meal_slot where household_id = ?", Integer.class, home))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from meal where household_id = ?", Integer.class, home))
        .isZero();
  }

  @Test
  void theWeekQueryReturnsOnlyItsSevenDatesOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-week-anna");
    long home = households.create(anna, "Pg week").id();
    long stew = meals.quickAdd(anna, home, "Stew").id();
    long soup = meals.quickAdd(anna, home, "Soup").id();
    planning.add(anna, home, MONDAY.plusDays(6), SlotType.SNACK, soup, null);
    planning.add(anna, home, MONDAY, SlotType.LUNCH, stew, null);
    planning.add(anna, home, MONDAY.plusWeeks(1), SlotType.LUNCH, soup, null);
    planning.add(anna, home, MONDAY.minusDays(1), SlotType.DINNER, soup, null);

    PlanWeek week = planning.week(anna, home, MONDAY.plusDays(3));

    assertThat(week.monday()).isEqualTo(MONDAY);
    assertThat(week.slots()).extracting(SlotView::mealId).containsExactly(stew, soup);
    assertThat(week.slots()).extracting(SlotView::date).containsExactly(MONDAY, MONDAY.plusDays(6));
  }

  @Test
  void theRecentMealListCountsEachMealOnceUpToTodayOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-recent-anna");
    long home = households.create(anna, "Pg recent").id();
    long stew = meals.quickAdd(anna, home, "Stew").id();
    long soup = meals.quickAdd(anna, home, "Soup").id();
    LocalDate today = LocalDate.now(clock);
    planning.add(anna, home, today.minusDays(4), SlotType.DINNER, stew, null);
    planning.add(anna, home, today.minusDays(1), SlotType.DINNER, soup, null);
    planning.add(anna, home, today.plusDays(2), SlotType.DINNER, stew, null);

    assertThat(planning.recentlyPlannedMealIds(anna, home, 5)).containsExactly(soup, stew);
  }

  @Test
  void aStaleWriteLosingTheRaceOnPostgresBecomesAConflict() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-race-anna");
    long home = households.create(anna, "Pg race").id();
    long stew = meals.quickAdd(anna, home, "Stew").id();
    SlotView slot = planning.add(anna, home, MONDAY, SlotType.LUNCH, stew, null);
    jdbc.update("update meal_slot set version = version + 1 where id = ?", slot.id());

    assertThatThrownBy(
            () -> planning.move(anna, home, slot.id(), slot.version(), MONDAY, SlotType.DINNER))
        .isInstanceOf(PlanConflictException.class);
    // Read through JDBC: the failed entity is still dirty in this transaction, so a JPA query
    // would flush it again and fail once more.
    assertThat(
            jdbc.queryForObject(
                "select slot_type from meal_slot where id = ?", String.class, slot.id()))
        .isEqualTo("LUNCH");
  }
}
