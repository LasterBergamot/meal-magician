package hu.lasterbergamot.mealmagician.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The meal library's queries on the real database. The search has optional parameters (a name
 * pattern, a tag, the archive flag) that must bind as null on Postgres too, and the composite
 * foreign keys must refuse a cross-household link there, not only on H2.
 */
@Transactional
@Tag("integration")
class PostgresMealTests extends AbstractPostgresIntegrationTest {

  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void searchWithOptionalFiltersWorksOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-meal-anna");
    long home = households.create(anna, "Pg home").id();
    meals.create(anna, home, new MealDetails("Lentil soup", null, null, List.of("vegan")));
    meals.create(anna, home, new MealDetails("Pasta", null, null, List.of("quick")));
    MealView old = meals.quickAdd(anna, home, "Soup of the past");
    meals.archive(anna, home, old.id());

    assertThat(names(meals.search(anna, home, new MealSearch(null, null, false))))
        .containsExactly("Lentil soup", "Pasta");
    assertThat(names(meals.search(anna, home, new MealSearch("SOUP", null, false))))
        .containsExactly("Lentil soup");
    assertThat(names(meals.search(anna, home, new MealSearch(null, "Vegan", false))))
        .containsExactly("Lentil soup");
    assertThat(names(meals.search(anna, home, new MealSearch("soup", null, true))))
        .containsExactly("Lentil soup", "Soup of the past");
    assertThat(meals.tagNames(anna, home)).containsExactly("quick", "vegan");
  }

  @Test
  void aStrangerAndAnotherHouseholdsMealAreRejectedOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-meal-owner");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-meal-other");
    long annasHome = households.create(anna, "Owner home").id();
    long belasHome = households.create(bela, "Other home").id();
    long stew = meals.quickAdd(anna, annasHome, "Stew").id();

    assertThatThrownBy(() -> meals.find(bela, annasHome, stew))
        .isInstanceOf(hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.find(bela, belasHome, stew))
        .isInstanceOf(MealNotFoundException.class);
  }

  @Test
  void theForeignKeysRefuseACrossHouseholdTagLinkOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-fk-anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-fk-bela");
    long annasHome = households.create(anna, "Fk anna").id();
    long belasHome = households.create(bela, "Fk bela").id();
    long annasStew = meals.quickAdd(anna, annasHome, "Stew").id();
    meals.create(bela, belasHome, new MealDetails("Pie", null, null, List.of("dessert")));
    long belasTag =
        jdbc.queryForObject(
            "select id from tag where household_id = ? and name_key = 'dessert'",
            Long.class,
            belasHome);

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into meal_tag (meal_id, tag_id, household_id) values (?, ?, ?)",
                annasStew,
                belasTag,
                annasHome))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static List<String> names(List<MealView> found) {
    return found.stream().map(MealView::name).toList();
  }
}
