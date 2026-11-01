package hu.lasterbergamot.mealmagician.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.household.internal.Membership;
import hu.lasterbergamot.mealmagician.household.internal.MembershipRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * The meal library rules, against the dev profile's H2 so the mappings meet the real schema. Each
 * test runs in a transaction that is rolled back, so the tests do not see each other's meals.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class MealServiceTest {

  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private MembershipRepository memberships;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;

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

  private MealDetails details(String name, String notes, String url, String... tags) {
    return new MealDetails(name, notes, url, List.of(tags));
  }

  private MealView addWithTags(String name, String... tags) {
    return meals.create(anna, annasHome, details(name, null, null, tags));
  }

  @Test
  void theCreatedMealKeepsItsDetailsAndSortsItsTags() {

    MealView created =
        meals.create(
            anna,
            annasHome,
            details(
                "  Lentil stew ",
                " Red lentils, 20 min ",
                "https://example.com/stew",
                "Vegan",
                "quick soup"));

    assertThat(created.name()).isEqualTo("Lentil stew");
    assertThat(created.notes()).isEqualTo("Red lentils, 20 min");
    assertThat(created.recipeUrl()).isEqualTo("https://example.com/stew");
    assertThat(created.tags()).containsExactly("quick soup", "Vegan");
    assertThat(created.archived()).isFalse();
    assertThat(created.version()).isZero();
  }

  @Test
  void blankNotesLinkAndTagsAreStoredAsNothing() {

    MealView created = meals.create(anna, annasHome, new MealDetails("Toast", "  ", "", null));

    assertThat(created.notes()).isNull();
    assertThat(created.recipeUrl()).isNull();
    assertThat(created.tags()).isEmpty();
  }

  @Test
  void quickAddNeedsOnlyAName() {

    MealView added = meals.quickAdd(anna, annasHome, "  Lentil soup ");

    assertThat(added.name()).isEqualTo("Lentil soup");
    assertThat(added.notes()).isNull();
    assertThat(added.recipeUrl()).isNull();
    assertThat(added.tags()).isEmpty();
  }

  @Test
  void quickAddRefusesABlankName() {

    assertThatThrownBy(() -> meals.quickAdd(anna, annasHome, "   "))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.NAME_REQUIRED);
  }

  @Test
  void aRefusedMealLeavesNoTagOrMealBehind() {

    assertThatThrownBy(() ->
            meals.create(
                anna, annasHome, details("Soup", null, "javascript:alert(1)", "Brand new tag")))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.RECIPE_URL_INVALID);
    assertThat(countRows("tag", annasHome)).isZero();
    assertThat(countRows("meal", annasHome)).isZero();
  }

  @Test
  void editReplacesTheDetailsAndTheTagSet() {
    MealView original = addWithTags("Stew", "vegan", "soup");

    MealView edited =
        meals.edit(
            anna,
            annasHome,
            original.id(),
            details("Lentil stew", "Smoky", "https://example.com/x", "Soup", "quick"));

    assertThat(edited.name()).isEqualTo("Lentil stew");
    assertThat(edited.notes()).isEqualTo("Smoky");
    assertThat(edited.recipeUrl()).isEqualTo("https://example.com/x");
    assertThat(edited.tags()).containsExactly("quick", "soup");
    assertThat(meals.find(anna, annasHome, original.id()).tags()).containsExactly("quick", "soup");
    assertThat(linkCount(original.id())).isEqualTo(2);
  }

  @Test
  void editWithoutTagsRemovesEveryTag() {
    MealView original = addWithTags("Stew", "vegan");

    MealView edited = meals.edit(anna, annasHome, original.id(), details("Stew", null, null));

    assertThat(edited.tags()).isEmpty();
    assertThat(linkCount(original.id())).isZero();
    assertThat(meals.tagNames(anna, annasHome)).isEmpty();
  }

  @Test
  void editingBumpsTheVersion() {
    MealView original = addWithTags("Stew");

    MealView edited = meals.edit(anna, annasHome, original.id(), details("Stew 2", null, null));

    assertThat(edited.version()).isEqualTo(original.version() + 1);
  }

  @Test
  void aRefusedEditChangesNothing() {
    MealView original = addWithTags("Stew", "vegan");

    assertThatThrownBy(
            () -> meals.edit(anna, annasHome, original.id(), details("  ", null, null, "soup")))
        .isInstanceOf(InvalidMealException.class);

    MealView unchanged = meals.find(anna, annasHome, original.id());
    assertThat(unchanged.name()).isEqualTo("Stew");
    assertThat(unchanged.tags()).containsExactly("vegan");
  }

  @Test
  void archivingHidesTheMealUntilItIsRestored() {
    MealView stew = addWithTags("Stew", "soup");
    addWithTags("Soup");

    MealView archived = meals.archive(anna, annasHome, stew.id());

    assertThat(archived.archived()).isTrue();
    assertThat(names(meals.search(anna, annasHome, new MealSearch(null, null, false))))
        .containsExactly("Soup");
    assertThat(names(meals.search(anna, annasHome, new MealSearch(null, null, true))))
        .containsExactly("Soup", "Stew");

    MealView restored = meals.restore(anna, annasHome, stew.id());

    assertThat(restored.archived()).isFalse();
    assertThat(names(meals.search(anna, annasHome, new MealSearch(null, null, false))))
        .containsExactly("Soup", "Stew");
  }

  @Test
  void searchMatchesPartOfTheNameIgnoringCaseInNameOrder() {
    addWithTags("Lentil soup");
    addWithTags("Carrot SOUP");
    addWithTags("Pasta");

    assertThat(names(meals.search(anna, annasHome, new MealSearch("soup", null, false))))
        .containsExactly("Carrot SOUP", "Lentil soup");
  }

  @Test
  void searchTreatsPercentAndUnderscoreAsOrdinaryCharacters() {
    addWithTags("100% juice");
    addWithTags("Ten_bean stew");
    addWithTags("Tenabean stew");
    addWithTags("wow! pie");

    assertThat(names(meals.search(anna, annasHome, new MealSearch("%", null, false))))
        .containsExactly("100% juice");
    assertThat(names(meals.search(anna, annasHome, new MealSearch("_", null, false))))
        .containsExactly("Ten_bean stew");
    assertThat(names(meals.search(anna, annasHome, new MealSearch("!", null, false))))
        .containsExactly("wow! pie");
  }

  @Test
  void blankOrMissingSearchFiltersReturnTheWholeLibrary() {
    addWithTags("Stew", "soup");
    addWithTags("Pasta");

    assertThat(names(meals.search(anna, annasHome, new MealSearch(null, null, false))))
        .containsExactly("Pasta", "Stew");
    assertThat(names(meals.search(anna, annasHome, new MealSearch("  ", "  ", false))))
        .containsExactly("Pasta", "Stew");
  }

  @Test
  void searchFiltersByTagIgnoringCase() {
    addWithTags("Stew", "Vegan");
    addWithTags("Pasta", "quick");
    addWithTags("Salad", "vegan", "quick");

    assertThat(names(meals.search(anna, annasHome, new MealSearch(null, "VEGAN", false))))
        .containsExactly("Salad", "Stew");
  }

  @Test
  void searchCombinesTheNameAndTheTag() {
    addWithTags("Stew", "vegan");
    addWithTags("Lentil soup", "vegan");
    addWithTags("Pasta", "quick");

    assertThat(names(meals.search(anna, annasHome, new MealSearch("soup", "vegan", false))))
        .containsExactly("Lentil soup");
  }

  @Test
  void aTagTheHouseholdDoesNotHaveFindsNothing() {
    addWithTags("Stew", "vegan");

    assertThat(meals.search(anna, annasHome, new MealSearch(null, "nonexistent", false))).isEmpty();
  }

  @Test
  void eachMealListsItsTagsInTheResults() {
    addWithTags("Stew", "vegan", "quick");

    MealView found = meals.search(anna, annasHome, new MealSearch(null, null, false)).get(0);

    assertThat(found.tags()).containsExactly("quick", "vegan");
  }

  @Test
  void tagNamesListOnlyTagsInUseInOrder() {
    addWithTags("Stew", "Vegan", "quick");
    MealView pasta = addWithTags("Pasta", "unused-soon");
    meals.edit(anna, annasHome, pasta.id(), details("Pasta", null, null));

    assertThat(meals.tagNames(anna, annasHome)).containsExactly("quick", "Vegan");
  }

  @Test
  void tagsBelongToOneHouseholdEvenWhenTheSpellingIsTheSame() {
    addWithTags("Stew", "Vegan");
    long belasStew =
        meals.create(bela, belasHome, details("Bela's stew", null, null, "vegan")).id();

    assertThat(meals.tagNames(anna, annasHome)).containsExactly("Vegan");
    assertThat(meals.tagNames(bela, belasHome)).containsExactly("vegan");
    assertThat(names(meals.search(anna, annasHome, new MealSearch(null, "vegan", false))))
        .containsExactly("Stew");
    assertThat(meals.find(bela, belasHome, belasStew).tags()).containsExactly("vegan");
  }

  @Test
  void aPlainMemberMayPlanTheLibrary() {
    memberships.save(new Membership(annasHome, bela, HouseholdRole.MEMBER, Instant.now()));

    MealView added = meals.quickAdd(bela, annasHome, "Pancakes");
    MealView edited = meals.edit(bela, annasHome, added.id(), details("Pancakes", null, null));

    assertThat(edited.name()).isEqualTo("Pancakes");
    assertThat(meals.find(anna, annasHome, added.id()).name()).isEqualTo("Pancakes");
  }

  @Test
  void aStrangerGetsNotFoundFromEveryMethod() {
    MealView stew = addWithTags("Stew");

    assertThatThrownBy(() -> meals.create(bela, annasHome, details("x", null, null)))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.quickAdd(bela, annasHome, "x"))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.edit(bela, annasHome, stew.id(), details("x", null, null)))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.archive(bela, annasHome, stew.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.restore(bela, annasHome, stew.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.find(bela, annasHome, stew.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.search(bela, annasHome, new MealSearch(null, null, false)))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> meals.tagNames(bela, annasHome))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void anotherHouseholdsMealIdIsNotFound() {
    MealView annasStew = addWithTags("Stew", "soup");

    assertThatThrownBy(() -> meals.find(bela, belasHome, annasStew.id()))
        .isInstanceOf(MealNotFoundException.class);
    assertThatThrownBy(
            () -> meals.edit(bela, belasHome, annasStew.id(), details("Hijacked", null, null)))
        .isInstanceOf(MealNotFoundException.class);
    assertThatThrownBy(() -> meals.archive(bela, belasHome, annasStew.id()))
        .isInstanceOf(MealNotFoundException.class);
    assertThatThrownBy(() -> meals.restore(bela, belasHome, annasStew.id()))
        .isInstanceOf(MealNotFoundException.class);

    assertThat(meals.find(anna, annasHome, annasStew.id()).name()).isEqualTo("Stew");
    assertThat(meals.find(anna, annasHome, annasStew.id()).archived()).isFalse();
  }

  @Test
  void aSearchNeverShowsAnotherHouseholdsMeals() {
    addWithTags("Stew");
    meals.create(bela, belasHome, details("Bela's pie", null, null));

    assertThat(names(meals.search(bela, belasHome, new MealSearch(null, null, false))))
        .containsExactly("Bela's pie");
  }

  @Test
  void changesPublishMealChangedAndReadsDoNot() {
    MealView stew = addWithTags("Stew");
    long afterCreate = events.stream(MealChanged.class).count();

    meals.find(anna, annasHome, stew.id());
    meals.search(anna, annasHome, new MealSearch(null, null, false));
    meals.tagNames(anna, annasHome);

    assertThat(afterCreate).isEqualTo(1);
    assertThat(events.stream(MealChanged.class).count()).isEqualTo(afterCreate);

    meals.edit(anna, annasHome, stew.id(), details("Stew", null, null));
    meals.archive(anna, annasHome, stew.id());
    meals.restore(anna, annasHome, stew.id());
    meals.quickAdd(anna, annasHome, "Pasta");

    assertThat(events.stream(MealChanged.class).count()).isEqualTo(5);
    assertThat(events.stream(MealChanged.class))
        .allSatisfy(event -> assertThat(event.householdId()).isEqualTo(annasHome));
  }

  @Test
  void theDatabaseRefusesATagLinkAcrossHouseholds() {
    MealView annasStew = addWithTags("Stew");
    meals.create(bela, belasHome, details("Pie", null, null, "dessert"));
    long belasTag = tagIdOf(belasHome, "dessert");

    // A link that says "Anna's household" but points at Bela's tag: the composite foreign key
    // refuses it, whatever the service would do.
    assertThatThrownBy(() ->
            jdbc.update(
                "insert into meal_tag (meal_id, tag_id, household_id) values (?, ?, ?)",
                annasStew.id(),
                belasTag,
                annasHome))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static List<String> names(List<MealView> found) {
    return found.stream().map(MealView::name).toList();
  }

  private long linkCount(long mealId) {
    return jdbc.queryForObject(
        "select count(*) from meal_tag where meal_id = ?", Long.class, mealId);
  }

  private long countRows(String table, long householdId) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where household_id = ?", Long.class, householdId);
  }

  private long tagIdOf(long householdId, String name) {
    return jdbc.queryForObject(
        "select id from tag where household_id = ? and name_key = ?",
        Long.class,
        householdId,
        name);
  }
}
