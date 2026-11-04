package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealService;
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
 * The draft tables on the real database (V15): the composite foreign keys that keep a draft and its
 * meals inside one household, and the either-meal-or-idea check. Each test rolls back.
 */
@Transactional
@Tag("integration")
class PostgresDraftTests extends AbstractPostgresIntegrationTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long draft;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "pg-draft-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "pg-draft-bela");
    annasHome = households.create(anna, "Draft anna").id();
    belasHome = households.create(bela, "Draft bela").id();
    jdbc.update(
        "insert into assistant_draft (household_id, created_by, week_start, status, request_text,"
            + " conversation, created_at) values (?, ?, ?, 'OPEN', 'x', '[]', now())",
        annasHome,
        anna,
        MONDAY);
    draft =
        jdbc.queryForObject(
            "select id from assistant_draft where household_id = ?", Long.class, annasHome);
  }

  private void insertItem(long draftId, long household, Long mealId, String idea) {
    jdbc.update(
        "insert into assistant_draft_item (draft_id, household_id, day_date, slot_type, position,"
            + " meal_id, idea_name) values (?, ?, ?, 'LUNCH', 0, ?, ?)",
        draftId,
        household,
        MONDAY,
        mealId,
        idea);
  }

  @Test
  void anItemMayNameAMealOfTheSameHouseholdOrBeAnIdea() {
    long own = meals.quickAdd(anna, annasHome, "Soup").id();

    insertItem(draft, annasHome, own, null);
    insertItem(draft, annasHome, null, "Fish soup");

    assertThat(jdbc.queryForObject("select count(*) from assistant_draft_item", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void anItemCannotNameAMealOfAnotherHousehold() {
    long theirs = meals.quickAdd(bela, belasHome, "Secret").id();

    assertThatThrownBy(() -> insertItem(draft, annasHome, theirs, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anItemCannotBelongToADraftOfAnotherHousehold() {
    assertThatThrownBy(() -> insertItem(draft, belasHome, null, "Idea"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anItemIsEitherAMealOrAnIdeaNeverBothNorNeither() {
    long own = meals.quickAdd(anna, annasHome, "Soup").id();

    assertThatThrownBy(() -> insertItem(draft, annasHome, own, "Both"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anItemWithNeitherAMealNorAnIdeaIsRefused() {
    assertThatThrownBy(() -> insertItem(draft, annasHome, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingTheHouseholdsDraftDeletesItsItems() {
    insertItem(draft, annasHome, null, "Idea");

    jdbc.update("delete from assistant_draft where id = ?", draft);

    assertThat(jdbc.queryForObject("select count(*) from assistant_draft_item", Integer.class))
        .isZero();
  }
}
