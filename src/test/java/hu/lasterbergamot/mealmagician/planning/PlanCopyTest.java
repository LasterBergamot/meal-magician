package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import hu.lasterbergamot.mealmagician.catalog.MealService;
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
 * Copying a day or a week of the plan (merge and replace), against the dev profile's H2. Each test
 * runs in a transaction that is rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class PlanCopyTest {

  /** A Monday in ISO week 45. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private static final LocalDate TUESDAY = MONDAY.plusDays(1);

  @Autowired private PlanningService planning;
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

  private SlotView add(LocalDate date, SlotType type, long meal) {
    return planning.add(anna, annasHome, date, type, meal, null);
  }

  private List<SlotView> day(LocalDate date) {
    return planning.week(anna, annasHome, date).slots().stream()
        .filter(slot -> slot.date().equals(date))
        .toList();
  }

  private static void assertProblem(Runnable action, PlanProblem problem) {
    assertThatThrownBy(action::run)
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(problem);
  }

  @Test
  void mergingADayAppendsAfterTheMealsAlreadyThere() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(TUESDAY, SlotType.LUNCH, soup);

    long eventsBefore = events.stream(SlotPlanned.class).count();

    int written = planning.copyDay(anna, annasHome, MONDAY, TUESDAY, CopyMode.MERGE);

    assertThat(written).isEqualTo(1);
    assertThat(day(TUESDAY))
        .extracting(SlotView::mealId, SlotView::position)
        .containsExactly(tuple(soup, 0), tuple(stew, 1));
    assertThat(day(MONDAY)).extracting(SlotView::mealId).containsExactly(stew);
    assertThat(events.stream(SlotPlanned.class).skip(eventsBefore))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.date()).isEqualTo(TUESDAY);
          assertThat(event.mealId()).isEqualTo(stew);
          assertThat(event.householdId()).isEqualTo(annasHome);
        });
  }

  @Test
  void copyingADayKeepsSlotTypesAndNotes() {
    planning.add(anna, annasHome, MONDAY, SlotType.BREAKFAST, stew, "n");
    add(MONDAY, SlotType.DINNER, soup);

    planning.copyDay(anna, annasHome, MONDAY, TUESDAY, CopyMode.MERGE);

    assertThat(day(TUESDAY))
        .extracting(SlotView::slotType, SlotView::mealId, SlotView::note)
        .containsExactly(tuple(SlotType.BREAKFAST, stew, "n"), tuple(SlotType.DINNER, soup, null));
  }

  @Test
  void replacingADayMakesItACopyOfTheSource() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(TUESDAY, SlotType.LUNCH, soup);
    add(TUESDAY, SlotType.SNACK, cake);

    planning.copyDay(anna, annasHome, MONDAY, TUESDAY, CopyMode.REPLACE);

    assertThat(day(TUESDAY))
        .extracting(SlotView::slotType, SlotView::mealId, SlotView::position)
        .containsExactly(tuple(SlotType.LUNCH, stew, 0));
  }

  @Test
  void copyingADayOntoItselfIsRefusedAndWritesNothing() {
    add(MONDAY, SlotType.LUNCH, stew);

    assertProblem(
        () -> planning.copyDay(anna, annasHome, MONDAY, MONDAY, CopyMode.REPLACE),
        PlanProblem.SAME_TARGET);
    assertThat(day(MONDAY)).hasSize(1);
    assertThat(events.stream(SlotPlanned.class).count()).isEqualTo(1);
  }

  @Test
  void copyingAnEmptyDayIsRefusedSoAReplaceNeverWipesADay() {
    add(TUESDAY, SlotType.LUNCH, soup);

    assertProblem(
        () -> planning.copyDay(anna, annasHome, MONDAY, TUESDAY, CopyMode.REPLACE),
        PlanProblem.NOTHING_TO_COPY);
    assertThat(day(TUESDAY)).extracting(SlotView::mealId).containsExactly(soup);
  }

  @Test
  void anArchivedMealIsCopiedAsItIs() {
    add(MONDAY, SlotType.LUNCH, stew);
    meals.archive(anna, annasHome, stew);

    planning.copyDay(anna, annasHome, MONDAY, TUESDAY, CopyMode.MERGE);

    assertThat(day(TUESDAY)).extracting(SlotView::mealId).containsExactly(stew);
  }

  @Test
  void mergingAWeekKeepsEachMealOnItsWeekday() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(MONDAY.plusDays(6), SlotType.DINNER, soup);

    int written = planning.copyWeek(anna, annasHome, MONDAY, MONDAY.plusWeeks(1), CopyMode.MERGE);

    assertThat(written).isEqualTo(2);
    assertThat(day(MONDAY.plusDays(7))).extracting(SlotView::mealId).containsExactly(stew);
    assertThat(day(MONDAY.plusDays(13))).extracting(SlotView::mealId).containsExactly(soup);
  }

  @Test
  void anyDayOfEitherWeekWorksForACopy() {
    add(MONDAY, SlotType.LUNCH, stew);

    planning.copyWeek(anna, annasHome, MONDAY.plusDays(2), MONDAY.plusDays(11), CopyMode.MERGE);

    assertThat(day(MONDAY.plusDays(7))).extracting(SlotView::mealId).containsExactly(stew);
  }

  @Test
  void replacingAWeekEmptiesTheWholeTargetWeek() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(MONDAY.plusDays(10), SlotType.LUNCH, soup);

    planning.copyWeek(anna, annasHome, MONDAY, MONDAY.plusWeeks(1), CopyMode.REPLACE);

    assertThat(day(MONDAY.plusDays(10))).isEmpty();
    assertThat(day(MONDAY.plusDays(7))).extracting(SlotView::mealId).containsExactly(stew);
  }

  @Test
  void copyingAWeekOntoItselfOrCopyingAnEmptyWeekIsRefused() {
    add(MONDAY, SlotType.LUNCH, stew);

    assertProblem(
        () -> planning.copyWeek(anna, annasHome, MONDAY, MONDAY.plusDays(4), CopyMode.MERGE),
        PlanProblem.SAME_TARGET);
    assertProblem(
        () ->
            planning.copyWeek(
                anna, annasHome, MONDAY.plusWeeks(2), MONDAY.plusWeeks(1), CopyMode.REPLACE),
        PlanProblem.NOTHING_TO_COPY);
  }

  @Test
  void aReplaceThatLosesTheRaceBecomesAConflict() {
    add(MONDAY, SlotType.LUNCH, stew);
    SlotView target = add(TUESDAY, SlotType.LUNCH, soup);
    // Another transaction changed the target slot after this one read it.
    jdbc.update("update meal_slot set version = version + 1 where id = ?", target.id());

    assertThatThrownBy(() -> planning.copyDay(anna, annasHome, MONDAY, TUESDAY, CopyMode.REPLACE))
        .isInstanceOf(PlanConflictException.class)
        .hasMessageContaining("2026-11-03");
  }

  @Test
  void anotherHouseholdsPlanIsInvisibleToACopy() {
    add(MONDAY, SlotType.LUNCH, stew);

    assertProblem(
        () -> planning.copyDay(bela, belasHome, MONDAY, TUESDAY, CopyMode.MERGE),
        PlanProblem.NOTHING_TO_COPY);
    assertProblem(
        () -> planning.copyWeek(bela, belasHome, MONDAY, MONDAY.plusWeeks(1), CopyMode.MERGE),
        PlanProblem.NOTHING_TO_COPY);
  }

  @Test
  void aStrangerCannotCopy() {
    long stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");
    add(MONDAY, SlotType.LUNCH, stew);

    assertThatThrownBy(() -> planning.copyDay(stranger, annasHome, MONDAY, TUESDAY, CopyMode.MERGE))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() ->
            planning.copyWeek(stranger, annasHome, MONDAY, MONDAY.plusWeeks(1), CopyMode.MERGE))
        .isInstanceOf(HouseholdNotFoundException.class);
  }
}
