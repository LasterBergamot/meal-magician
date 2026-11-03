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

  @Test
  void theForeignKeysRefuseACrossHouseholdIngredientLinkOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-ing-fk-anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-ing-fk-bela");
    long annasHome = households.create(anna, "Ing fk anna").id();
    long belasHome = households.create(bela, "Ing fk bela").id();
    long annasStew = meals.quickAdd(anna, annasHome, "Stew").id();
    meals.create(bela, belasHome, new MealDetails("Pie", null, null, null, null, List.of("alma")));
    long belasIngredient =
        jdbc.queryForObject(
            "select id from ingredient where household_id = ? and name_key = 'alma'",
            Long.class,
            belasHome);

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into meal_ingredient (meal_id, ingredient_id, household_id) values (?, ?, ?)",
                annasStew,
                belasIngredient,
                annasHome))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void ingredientsAreSavedSearchedAndSuggestedOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-ing-anna");
    long home = households.create(anna, "Ing anna").id();
    meals.create(
        anna, home, new MealDetails("Omelette", null, null, null, null, List.of("Tojás", "tojas")));
    meals.quickAdd(anna, home, "Toast");

    assertThat(
            meals
                .find(
                    anna,
                    home,
                    meals.search(anna, home, new MealSearch("omel", null, false)).get(0).id())
                .ingredients())
        .containsExactly("Tojás");
    assertThat(names(meals.search(anna, home, new MealSearch(null, null, "TOJAS", false))))
        .containsExactly("Omelette");
    assertThat(meals.ingredientSuggestions(anna, home, "tojas", 5))
        .containsExactly(new IngredientSuggestion("Tojás", true));
    assertThat(meals.ingredientSuggestions(anna, home, "lisz", 5))
        .contains(new IngredientSuggestion("liszt", false));
    assertThat(meals.ingredientSuggestions(anna, home, "", 5)).hasSize(5);
  }

  @Test
  void occasionTypesAreStoredOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-occ-anna");
    long annasHome = households.create(anna, "Occ anna").id();
    MealView cake =
        meals.create(
            anna,
            annasHome,
            new MealDetails("Cake", null, null, null, List.of(OccasionType.BIRTHDAY)));

    assertThat(meals.find(anna, annasHome, cake.id()).occasionTypes())
        .containsExactly(OccasionType.BIRTHDAY);
  }

  @Test
  void theForeignKeyRefusesACrossHouseholdOccasionTypeOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-occ-fk-anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-occ-fk-bela");
    long annasHome = households.create(anna, "Occ fk anna").id();
    long belasHome = households.create(bela, "Occ fk bela").id();
    long cake = meals.quickAdd(anna, annasHome, "Cake").id();

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into meal_occasion_type (meal_id, occasion_type, household_id) values (?, 'PARTY', ?)",
                cake,
                belasHome))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theCheckConstraintRefusesAnUnknownOccasionTypeOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-occ-ck-anna");
    long annasHome = households.create(anna, "Occ ck anna").id();
    long cake = meals.quickAdd(anna, annasHome, "Cake").id();

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into meal_occasion_type (meal_id, occasion_type, household_id) values (?, 'FEAST', ?)",
                cake,
                annasHome))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static List<String> names(List<MealView> found) {
    return found.stream().map(MealView::name).toList();
  }
}
