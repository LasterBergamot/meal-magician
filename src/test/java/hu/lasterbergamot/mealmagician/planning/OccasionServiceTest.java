package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import hu.lasterbergamot.mealmagician.catalog.MealChanged;
import hu.lasterbergamot.mealmagician.catalog.MealNotFoundException;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occasions and their menus, against the dev profile's H2. Each test runs in a transaction that is
 * rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class OccasionServiceTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  private static final LocalDate XMAS = LocalDate.of(2026, 12, 24);

  @Autowired private OccasionService occasions;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long stew;
  private long soup;
  private long cake;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
    soup = meals.quickAdd(anna, annasHome, "Soup").id();
    cake = meals.quickAdd(anna, annasHome, "Cake").id();
  }

  private OccasionView create(String name, LocalDate date) {
    return occasions.create(
        anna, annasHome, new OccasionDetails(name, date, OccasionType.PARTY, null));
  }

  private int count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  private static void assertProblem(Runnable action, PlanProblem problem) {
    assertThatThrownBy(action::run)
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(problem);
  }

  private List<Integer> positions(OccasionView view) {
    return view.menu().stream().map(MenuItemView::position).toList();
  }

  @Test
  void creatingTrimsTheNameAndTurnsBlankNotesIntoNone() {
    OccasionView created =
        occasions.create(
            anna, annasHome, new OccasionDetails(" Szülinap ", XMAS, OccasionType.BIRTHDAY, "  "));

    assertThat(created.name()).isEqualTo("Szülinap");
    assertThat(created.notes()).isNull();
    assertThat(created.date()).isEqualTo(XMAS);
    assertThat(created.type()).isEqualTo(OccasionType.BIRTHDAY);
    assertThat(created.version()).isZero();
    assertThat(created.menu()).isEmpty();
  }

  @Test
  void eachBrokenFieldIsReportedAndNothingIsWritten() {
    assertProblem(
        () ->
            occasions.create(
                anna, annasHome, new OccasionDetails(" ", XMAS, OccasionType.PARTY, null)),
        PlanProblem.NAME_REQUIRED);
    assertProblem(
        () ->
            occasions.create(
                anna,
                annasHome,
                new OccasionDetails("x".repeat(101), XMAS, OccasionType.PARTY, null)),
        PlanProblem.NAME_TOO_LONG);
    assertProblem(
        () ->
            occasions.create(
                anna, annasHome, new OccasionDetails("a", null, OccasionType.PARTY, null)),
        PlanProblem.DATE_REQUIRED);
    assertProblem(
        () -> occasions.create(anna, annasHome, new OccasionDetails("a", XMAS, null, null)),
        PlanProblem.TYPE_REQUIRED);
    assertProblem(
        () ->
            occasions.create(
                anna,
                annasHome,
                new OccasionDetails("a", XMAS, OccasionType.PARTY, "x".repeat(2001))),
        PlanProblem.NOTES_TOO_LONG);
    assertThat(count("occasion")).isZero();
  }

  @Test
  void anotherHouseholdsOccasionIsNotFound() {
    long id = create("Mine", XMAS).id();

    assertThatThrownBy(() -> occasions.find(bela, belasHome, id))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThatThrownBy(() ->
            occasions.edit(
                bela, belasHome, id, 0, new OccasionDetails("x", XMAS, OccasionType.PARTY, null)))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThatThrownBy(() -> occasions.delete(bela, belasHome, id, 0))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThatThrownBy(() -> occasions.addItem(bela, belasHome, id, stew))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThatThrownBy(() -> occasions.removeItem(bela, belasHome, id, 1))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThatThrownBy(() -> occasions.moveItem(bela, belasHome, id, 1, 0))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThat(occasions.list(bela, belasHome)).isEmpty();
  }

  @Test
  void theListIsByDateThenIdAndCountsItems() {
    OccasionView late = create("Late", XMAS);
    OccasionView early = create("Early", MONDAY);
    OccasionView sameDay = create("Same day", MONDAY);
    occasions.addItem(anna, annasHome, early.id(), stew);
    occasions.addItem(anna, annasHome, early.id(), soup);

    List<OccasionSummary> list = occasions.list(anna, annasHome);

    assertThat(list)
        .extracting(OccasionSummary::id, OccasionSummary::itemCount)
        .containsExactly(tuple(early.id(), 2), tuple(sameDay.id(), 0), tuple(late.id(), 0));
  }

  @Test
  void betweenIncludesBothEnds() {
    OccasionView monday = create("Mon", MONDAY);
    OccasionView sunday = create("Sun", MONDAY.plusDays(6));
    create("Next Monday", MONDAY.plusDays(7));

    assertThat(occasions.between(anna, annasHome, MONDAY, MONDAY.plusDays(6)))
        .extracting(OccasionSummary::id)
        .containsExactly(monday.id(), sunday.id());
  }

  @Test
  void editingChangesTheFieldsAndKeepsTheMenu() {
    OccasionView created = create("Old", MONDAY);
    occasions.addItem(anna, annasHome, created.id(), stew);

    OccasionView edited =
        occasions.edit(
            anna,
            annasHome,
            created.id(),
            created.version(),
            new OccasionDetails(" New ", XMAS, OccasionType.HOLIDAY, "Notes"));

    assertThat(edited.name()).isEqualTo("New");
    assertThat(edited.date()).isEqualTo(XMAS);
    assertThat(edited.type()).isEqualTo(OccasionType.HOLIDAY);
    assertThat(edited.notes()).isEqualTo("Notes");
    assertThat(edited.version()).isEqualTo(created.version() + 1);
    assertThat(edited.menu()).hasSize(1);
  }

  @Test
  void aStaleEditIsRefusedAndNothingChanges() {
    OccasionView created = create("Old", MONDAY);
    occasions.edit(
        anna,
        annasHome,
        created.id(),
        created.version(),
        new OccasionDetails("Newer", MONDAY, OccasionType.PARTY, null));

    assertThatThrownBy(() ->
            occasions.edit(
                anna,
                annasHome,
                created.id(),
                created.version(),
                new OccasionDetails("Older", MONDAY, OccasionType.PARTY, null)))
        .isInstanceOf(OccasionConflictException.class);
    assertThat(occasions.find(anna, annasHome, created.id()).name()).isEqualTo("Newer");
  }

  @Test
  void anEditThatLosesTheRaceBecomesAConflict() {
    OccasionView created = create("Old", MONDAY);
    jdbc.update("update occasion set version = version + 1 where id = ?", created.id());

    assertThatThrownBy(() ->
            occasions.edit(
                anna,
                annasHome,
                created.id(),
                created.version(),
                new OccasionDetails("x", MONDAY, OccasionType.PARTY, null)))
        .isInstanceOf(OccasionConflictException.class);
  }

  @Test
  void anInvalidEditIsRefused() {
    OccasionView created = create("Old", MONDAY);

    assertProblem(
        () ->
            occasions.edit(
                anna,
                annasHome,
                created.id(),
                created.version(),
                new OccasionDetails("", MONDAY, OccasionType.PARTY, null)),
        PlanProblem.NAME_REQUIRED);
  }

  @Test
  void deletingRemovesTheOccasionAndItsMenu() {
    OccasionView created = create("Gone", MONDAY);
    occasions.addItem(anna, annasHome, created.id(), stew);

    occasions.delete(anna, annasHome, created.id(), created.version());

    assertThatThrownBy(() -> occasions.find(anna, annasHome, created.id()))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThat(count("occasion_item")).isZero();
  }

  @Test
  void aStaleDeleteIsRefused() {
    OccasionView created = create("Keep", MONDAY);
    occasions.edit(
        anna,
        annasHome,
        created.id(),
        created.version(),
        new OccasionDetails("Kept", MONDAY, OccasionType.PARTY, null));

    assertThatThrownBy(() -> occasions.delete(anna, annasHome, created.id(), created.version()))
        .isInstanceOf(OccasionConflictException.class);
    assertThat(count("occasion")).isEqualTo(1);
  }

  @Test
  void addingMealsAppendsToTheMenu() {
    OccasionView created = create("Party", MONDAY);

    occasions.addItem(anna, annasHome, created.id(), stew);
    OccasionView after = occasions.addItem(anna, annasHome, created.id(), soup);

    assertThat(positions(after)).containsExactly(0, 1);
    assertThat(after.menu()).extracting(MenuItemView::mealId).containsExactly(stew, soup);
  }

  @Test
  void aMealOfAnotherHouseholdCannotBeAdded() {
    long belasPie = meals.quickAdd(bela, belasHome, "Pie").id();
    OccasionView created = create("Party", MONDAY);

    assertThatThrownBy(() -> occasions.addItem(anna, annasHome, created.id(), belasPie))
        .isInstanceOf(MealNotFoundException.class);
    assertThat(count("occasion_item")).isZero();
  }

  @Test
  void quickAddingCreatesTheMealAndTheItem() {
    OccasionView created = create("Party", MONDAY);

    OccasionView after = occasions.quickAddItem(anna, annasHome, created.id(), "Torta");

    assertThat(after.menu()).hasSize(1);
    assertThat(meals.find(anna, annasHome, after.menu().get(0).mealId()).name()).isEqualTo("Torta");
    assertThat(events.stream(MealChanged.class).count()).isGreaterThan(0);
  }

  @Test
  void quickAddingABlankNameAddsNothing() {
    OccasionView created = create("Party", MONDAY);

    assertThatThrownBy(() -> occasions.quickAddItem(anna, annasHome, created.id(), " "))
        .isInstanceOf(InvalidMealException.class);
    assertThat(count("occasion_item")).isZero();
  }

  @Test
  void quickAddingToAnotherHouseholdsOccasionCreatesNoMeal() {
    OccasionView created = create("Party", MONDAY);
    int mealsBefore = count("meal");

    assertThatThrownBy(() -> occasions.quickAddItem(bela, belasHome, created.id(), "Torta"))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThat(count("meal")).isEqualTo(mealsBefore);
  }

  @Test
  void removingTheMiddleItemRenumbersTheRest() {
    OccasionView created = create("Party", MONDAY);
    occasions.addItem(anna, annasHome, created.id(), stew);
    occasions.addItem(anna, annasHome, created.id(), soup);
    OccasionView full = occasions.addItem(anna, annasHome, created.id(), cake);

    OccasionView after =
        occasions.removeItem(anna, annasHome, created.id(), full.menu().get(1).id());

    assertThat(after.menu()).extracting(MenuItemView::mealId).containsExactly(stew, cake);
    assertThat(positions(after)).containsExactly(0, 1);
    assertThat(positions(occasions.find(anna, annasHome, created.id()))).containsExactly(0, 1);
  }

  @Test
  void anUnknownOrForeignItemIsNotFound() {
    OccasionView first = create("First", MONDAY);
    OccasionView second = create("Second", MONDAY);
    OccasionView withItem = occasions.addItem(anna, annasHome, second.id(), stew);
    long otherOccasionsItem = withItem.menu().get(0).id();

    assertThatThrownBy(() -> occasions.removeItem(anna, annasHome, first.id(), 999_999))
        .isInstanceOf(MenuItemNotFoundException.class);
    assertThatThrownBy(() -> occasions.removeItem(anna, annasHome, first.id(), otherOccasionsItem))
        .isInstanceOf(MenuItemNotFoundException.class);
    assertThatThrownBy(() -> occasions.moveItem(anna, annasHome, first.id(), otherOccasionsItem, 0))
        .isInstanceOf(MenuItemNotFoundException.class);
  }

  @Test
  void movingAnItemClampsThePlaceToTheMenu() {
    OccasionView created = create("Party", MONDAY);
    occasions.addItem(anna, annasHome, created.id(), stew);
    occasions.addItem(anna, annasHome, created.id(), soup);
    OccasionView full = occasions.addItem(anna, annasHome, created.id(), cake);
    long middle = full.menu().get(1).id();

    OccasionView first = occasions.moveItem(anna, annasHome, created.id(), middle, -5);
    assertThat(first.menu()).extracting(MenuItemView::mealId).containsExactly(soup, stew, cake);

    OccasionView last = occasions.moveItem(anna, annasHome, created.id(), middle, 99);
    assertThat(last.menu()).extracting(MenuItemView::mealId).containsExactly(stew, cake, soup);

    OccasionView second = occasions.moveItem(anna, annasHome, created.id(), middle, 1);
    assertThat(second.menu()).extracting(MenuItemView::mealId).containsExactly(stew, soup, cake);
    assertThat(positions(second)).containsExactly(0, 1, 2);
  }

  @Test
  void aStrangerIsRefusedByEveryMethod() {
    OccasionView created = create("Party", MONDAY);
    long stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");
    OccasionDetails details = new OccasionDetails("x", MONDAY, OccasionType.PARTY, null);

    assertThatThrownBy(() -> occasions.create(stranger, annasHome, details))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.find(stranger, annasHome, created.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.list(stranger, annasHome))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.between(stranger, annasHome, MONDAY, MONDAY))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.edit(stranger, annasHome, created.id(), 0, details))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.delete(stranger, annasHome, created.id(), 0))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.addItem(stranger, annasHome, created.id(), stew))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.quickAddItem(stranger, annasHome, created.id(), "x"))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.removeItem(stranger, annasHome, created.id(), 1))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> occasions.moveItem(stranger, annasHome, created.id(), 1, 0))
        .isInstanceOf(HouseholdNotFoundException.class);
  }
}
