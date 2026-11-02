package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan templates, occasions and occasion templates on the real database: the composite foreign keys
 * and checks of V9 must refuse bad rows, a household's delete must cascade, a used meal cannot be
 * deleted, and copy and the optimistic lock must work on Postgres too. A refused statement aborts
 * the Postgres transaction, so it is always the last line of its test.
 */
@Transactional
@Tag("integration")
class PostgresReuseTests extends AbstractPostgresIntegrationTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private PlanningService planning;
  @Autowired private PlanTemplateService planTemplates;
  @Autowired private OccasionService occasions;
  @Autowired private OccasionTemplateService occasionTemplates;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long stew;
  private long belasPie;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "pg-reuse-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "pg-reuse-bela");
    annasHome = households.create(anna, "Pg reuse anna").id();
    belasHome = households.create(bela, "Pg reuse bela").id();
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
    belasPie = meals.quickAdd(bela, belasHome, "Pie").id();
  }

  private long annasTemplate() {
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);
    return planTemplates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Day").id();
  }

  private long annasOccasion() {
    return occasions
        .create(anna, annasHome, new OccasionDetails("Party", MONDAY, OccasionType.PARTY, null))
        .id();
  }

  private int countFor(String table, long householdId) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where household_id = ?", Integer.class, householdId);
  }

  @Test
  void aTemplateItemCannotPointAtAnotherHouseholdsMeal() {
    long template = annasTemplate();

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into plan_template_item (template_id, household_id, day_offset,"
                    + " slot_type, position, meal_id) values (?, ?, 0, 'LUNCH', 5, ?)",
                template,
                annasHome,
                belasPie))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aMenuItemCannotLinkAnotherHouseholdsOccasionUnderThisHouseholdsId() {
    long belasOccasion =
        occasions
            .create(bela, belasHome, new OccasionDetails("Belas", MONDAY, OccasionType.PARTY, null))
            .id();

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into occasion_item (occasion_id, household_id, position, meal_id)"
                    + " values (?, ?, 0, ?)",
                belasOccasion,
                annasHome,
                stew))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aTemplateItemOffsetOfSevenIsRefused() {
    long template = annasTemplate();

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into plan_template_item (template_id, household_id, day_offset,"
                    + " slot_type, position, meal_id) values (?, ?, 7, 'LUNCH', 5, ?)",
                template,
                annasHome,
                stew))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingTheHouseholdRemovesAllSixTables() {
    annasTemplate();
    long occasion = annasOccasion();
    occasions.addItem(anna, annasHome, occasion, stew);
    occasionTemplates.saveFromOccasion(anna, annasHome, occasion, "Menu");

    jdbc.update("delete from household where id = ?", annasHome);

    for (String table :
        new String[] {
          "plan_template",
          "plan_template_item",
          "occasion",
          "occasion_item",
          "occasion_template",
          "occasion_template_item"
        }) {
      assertThat(countFor(table, annasHome)).as(table).isZero();
    }
  }

  @Test
  void aMealUsedOnlyByATemplateCannotBeDeleted() {
    long onlyTemplated = meals.quickAdd(anna, annasHome, "Templated").id();
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, onlyTemplated, null);
    planTemplates.save(anna, annasHome, TemplateScope.DAY, MONDAY, "Day");
    jdbc.update("delete from meal_slot where meal_id = ?", onlyTemplated);

    assertThatThrownBy(() -> jdbc.update("delete from meal where id = ?", onlyTemplated))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void replacingAWeekMakesTheTargetEqualToTheSourceOnPostgres() {
    long soup = meals.quickAdd(anna, annasHome, "Soup").id();
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);
    planning.add(anna, annasHome, MONDAY.plusWeeks(1).plusDays(3), SlotType.SNACK, soup, null);

    planning.copyWeek(anna, annasHome, MONDAY, MONDAY.plusWeeks(1), CopyMode.REPLACE);

    PlanWeek target = planning.week(anna, annasHome, MONDAY.plusWeeks(1));
    assertThat(target.slots()).hasSize(1);
    assertThat(target.slots().get(0).mealId()).isEqualTo(stew);
    assertThat(target.slots().get(0).date()).isEqualTo(MONDAY.plusWeeks(1));
  }

  @Test
  void aStaleOccasionEditLosingTheRaceBecomesAConflictOnPostgres() {
    OccasionView created =
        occasions.create(
            anna, annasHome, new OccasionDetails("Old", MONDAY, OccasionType.PARTY, null));
    jdbc.update("update occasion set version = version + 1 where id = ?", created.id());

    assertThatThrownBy(() ->
            occasions.edit(
                anna,
                annasHome,
                created.id(),
                created.version(),
                new OccasionDetails("New", MONDAY, OccasionType.PARTY, null)))
        .isInstanceOf(OccasionConflictException.class);
    // Read through JDBC: the failed entity is still dirty in this transaction, so a JPA query
    // would flush it again and fail once more.
    assertThat(
            jdbc.queryForObject(
                "select name from occasion where id = ?", String.class, created.id()))
        .isEqualTo("Old");
  }
}
