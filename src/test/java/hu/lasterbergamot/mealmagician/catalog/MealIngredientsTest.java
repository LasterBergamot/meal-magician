package hu.lasterbergamot.mealmagician.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingredients on meals and the picker's suggestions, against the dev profile's H2 so the mappings
 * meet the real V12 schema. Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class MealIngredientsTest {

  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
  }

  private MealView add(String name, String... ingredients) {
    return meals.create(
        anna, annasHome, new MealDetails(name, null, null, null, null, List.of(ingredients)));
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private static List<String> names(List<IngredientSuggestion> suggestions) {
    return suggestions.stream().map(IngredientSuggestion::name).toList();
  }

  @Test
  void spellingsOfOneIngredientAreOneRowThatKeepsTheFirstSpelling() {
    MealView created = add("Omelette", "Tojás", "tojas", "  TOJÁS ", "liszt");

    assertThat(created.ingredients()).containsExactly("liszt", "Tojás");
    assertThat(meals.find(anna, annasHome, created.id()).ingredients())
        .containsExactly("liszt", "Tojás");
    assertThat(count("select count(*) from ingredient where household_id = ?", annasHome))
        .isEqualTo(2);
  }

  @Test
  void anExistingIngredientKeepsItsDisplayFormWhenAnotherMealTypesItDifferently() {
    add("Omelette", "Tojás");

    MealView second = add("Cake", "TOJAS");

    assertThat(second.ingredients()).containsExactly("Tojás");
    assertThat(count("select count(*) from ingredient where household_id = ?", annasHome))
        .isEqualTo(1);
  }

  @Test
  void aMealWithoutIngredientsHasAnEmptyList() {
    MealView quick = meals.quickAdd(anna, annasHome, "Toast");

    assertThat(quick.ingredients()).isEmpty();
    assertThat(meals.find(anna, annasHome, quick.id()).ingredients()).isEmpty();
  }

  @Test
  void editingReplacesTheSetAndKeepsTheDroppedIngredientInTheHousehold() {
    MealView omelette = add("Omelette", "tojás", "liszt");

    MealView edited =
        meals.edit(
            anna,
            annasHome,
            omelette.id(),
            new MealDetails("Omelette", null, null, null, null, List.of("tojás", "tej")));

    assertThat(edited.ingredients()).containsExactly("tej", "tojás");
    assertThat(count("select count(*) from meal_ingredient where meal_id = ?", omelette.id()))
        .isEqualTo(2);
    assertThat(count("select count(*) from ingredient where name_key = 'liszt'")).isEqualTo(1);
  }

  @Test
  void editingWithNoIngredientsRemovesThemAll() {
    MealView omelette = add("Omelette", "tojás", "liszt");

    MealView edited =
        meals.edit(
            anna,
            annasHome,
            omelette.id(),
            new MealDetails("Omelette", null, null, null, null, null));

    assertThat(edited.ingredients()).isEmpty();
    assertThat(count("select count(*) from meal_ingredient where meal_id = ?", omelette.id()))
        .isZero();
  }

  @Test
  void aRefusedIngredientLeavesNothingBehind() {
    List<String> tooMany = IntStream.rangeClosed(1, 31).mapToObj(i -> "item " + i).toList();

    assertThatThrownBy(() ->
            meals.create(anna, annasHome, new MealDetails("Stew", null, null, null, null, tooMany)))
        .isInstanceOf(InvalidMealException.class);
    assertThatThrownBy(() ->
            meals.create(
                anna, annasHome, new MealDetails("Stew", null, null, null, null, List.of(" "))))
        .isInstanceOf(InvalidMealException.class);

    assertThat(count("select count(*) from ingredient where household_id = ?", annasHome)).isZero();
    assertThat(count("select count(*) from meal where household_id = ?", annasHome)).isZero();
  }

  @Test
  void aStrangerCannotUseIngredients() {
    assertThatThrownBy(() ->
            meals.create(
                bela, annasHome, new MealDetails("Stew", null, null, null, null, List.of("liszt"))))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.ingredientSuggestions(bela, annasHome, "l", 5))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  // ---- search ----

  @Test
  void searchByIngredientIgnoresCaseAndAccentsAndReturnsOnlyMealsHavingIt() {
    add("Omelette", "tojás", "liszt");
    add("Bread", "liszt");
    add("Fried egg", "Tojás");

    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, "TOJAS", false)))
        .extracting(MealView::name)
        .containsExactly("Fried egg", "Omelette");
    assertThat(meals.search(anna, annasHome, new MealSearch("omel", null, "tojas", false)))
        .extracting(MealView::name)
        .containsExactly("Omelette");
  }

  @Test
  void searchResultsCarryTheirIngredients() {
    add("Omelette", "tojás", "liszt");
    meals.quickAdd(anna, annasHome, "Toast");

    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, false)))
        .extracting(MealView::name, MealView::ingredients)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Omelette", List.of("liszt", "tojás")),
            org.assertj.core.groups.Tuple.tuple("Toast", List.of()));
  }

  @Test
  void searchByAnIngredientTheHouseholdDoesNotHaveFindsNothing() {
    add("Omelette", "tojás");

    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, "szalonna", false)))
        .isEmpty();
  }

  @Test
  void aBlankIngredientFilterMeansNoFilter() {
    add("Omelette", "tojás");

    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, "  ", false))).hasSize(1);
  }

  @Test
  void searchByIngredientKeepsTheArchiveRule() {
    MealView old = add("Old omelette", "tojás");
    add("New omelette", "tojás");
    meals.archive(anna, annasHome, old.id());

    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, "tojás", false)))
        .extracting(MealView::name)
        .containsExactly("New omelette");
    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, "tojás", true)))
        .extracting(MealView::name)
        .containsExactly("New omelette", "Old omelette");
  }

  @Test
  void anotherHouseholdsMealsNeverMatchAnIngredientSearch() {
    meals.create(
        bela,
        belasHome,
        new MealDetails("Belas omelette", null, null, null, null, List.of("tojás")));
    add("Omelette", "liszt");

    assertThat(meals.search(anna, annasHome, new MealSearch(null, null, "tojás", false))).isEmpty();
  }

  // ---- suggestions ----

  @Test
  void aSeedNameIsSuggestedWhenTheHouseholdDoesNotHaveIt() {
    List<IngredientSuggestion> found = meals.ingredientSuggestions(anna, annasHome, "tojas", 10);

    assertThat(found).contains(new IngredientSuggestion("tojás", false));
    assertThat(found).allMatch(suggestion -> !suggestion.known());
  }

  @Test
  void theHouseholdsOwnSpellingComesFirstAndTheSeedEntryIsNotRepeated() {
    add("Omelette", "Tojás");

    List<IngredientSuggestion> found = meals.ingredientSuggestions(anna, annasHome, "tojas", 10);

    assertThat(found.get(0)).isEqualTo(new IngredientSuggestion("Tojás", true));
    assertThat(names(found)).doesNotContain("tojás");
    assertThat(found).filteredOn(IngredientSuggestion::known).hasSize(1);
  }

  @Test
  void householdIngredientsComeBeforeSeedOnesAndNamesStartingWithTheTextBeforeOthers() {
    add("Soup", "Zeller", "húsleves zöldség");

    List<IngredientSuggestion> found = meals.ingredientSuggestions(anna, annasHome, "zell", 10);

    assertThat(found.get(0)).isEqualTo(new IngredientSuggestion("Zeller", true));
    assertThat(names(found)).doesNotContain("zeller");
  }

  @Test
  void anIngredientNotOnAnyMealIsStillSuggestedToItsHousehold() {
    MealView soup = add("Soup", "tökmag");
    meals.edit(
        anna, annasHome, soup.id(), new MealDetails("Soup", null, null, null, null, List.of()));

    assertThat(meals.ingredientSuggestions(anna, annasHome, "tökm", 5))
        .containsExactly(new IngredientSuggestion("tökmag", true));
  }

  @Test
  void aMatchInTheMiddleOfANameIsFoundAfterOnesThatStartWithTheText() {
    List<IngredientSuggestion> found = meals.ingredientSuggestions(anna, annasHome, "hagyma", 50);

    assertThat(names(found)).contains("vöröshagyma", "fokhagyma");
    List<IngredientSuggestion> starting =
        meals.ingredientSuggestions(anna, annasHome, "csirke", 50);
    assertThat(names(starting).get(0)).startsWith("csirke");
  }

  @Test
  void theLimitIsRespectedAndClamped() {
    assertThat(meals.ingredientSuggestions(anna, annasHome, "", 5)).hasSize(5);
    assertThat(meals.ingredientSuggestions(anna, annasHome, null, 0)).hasSize(1);
    assertThat(meals.ingredientSuggestions(anna, annasHome, "a", 1000)).hasSize(50);
  }

  @Test
  void whenTheHouseholdAloneFillsTheLimitNoStarterNamesAreAdded() {
    add("Soup", "alma", "almaecet");

    List<IngredientSuggestion> found = meals.ingredientSuggestions(anna, annasHome, "alma", 2);

    assertThat(found)
        .containsExactly(
            new IngredientSuggestion("alma", true), new IngredientSuggestion("almaecet", true));
  }

  @Test
  void withNothingTypedTheMostUsedIngredientsComeFirst() {
    add("A", "ritka", "gyakori");
    add("B", "gyakori");
    add("C", "gyakori", "ritka");

    List<IngredientSuggestion> found = meals.ingredientSuggestions(anna, annasHome, " ", 5);

    assertThat(names(found).subList(0, 2)).containsExactly("gyakori", "ritka");
    assertThat(found.get(0).known()).isTrue();
    assertThat(found.get(2).known()).isFalse();
    assertThat(found).hasSize(5);
  }

  @Test
  void wildcardCharactersInTheQueryMatchOnlyThemselves() {
    assertThat(meals.ingredientSuggestions(anna, annasHome, "%", 10)).isEmpty();
    assertThat(meals.ingredientSuggestions(anna, annasHome, "_", 10)).isEmpty();
    assertThat(meals.ingredientSuggestions(anna, annasHome, "!", 10)).isEmpty();
  }

  @Test
  void anotherHouseholdsIngredientsAreNeverSuggested() {
    meals.create(
        bela,
        belasHome,
        new MealDetails("Belas dish", null, null, null, null, List.of("titkos hozzávaló")));

    assertThat(meals.ingredientSuggestions(anna, annasHome, "titkos", 10)).isEmpty();
    assertThat(meals.ingredientSuggestions(anna, annasHome, "", 50))
        .noneMatch(suggestion -> suggestion.name().contains("titkos"));
    assertThat(meals.ingredientSuggestions(bela, belasHome, "titkos", 10))
        .containsExactly(new IngredientSuggestion("titkos hozzávaló", true));
  }

  @Test
  void aTagIsCreatedOnceWhenTwoMealsUseItInTheSameTransaction() {
    meals.create(anna, annasHome, new MealDetails("A", null, null, List.of("Vegán")));
    meals.create(anna, annasHome, new MealDetails("B", null, null, List.of("vegán")));

    assertThat(count("select count(*) from tag where household_id = ?", annasHome)).isEqualTo(1);
  }
}
