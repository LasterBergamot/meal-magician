package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealNotFoundException;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.Clock;
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
 * The weekly plan's rules, against the dev profile's H2 so the mappings meet the real schema. Each
 * test runs in a transaction that is rolled back, so the tests do not see each other's slots.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class PlanningServiceTest {

  /** A Monday, so the week of this date runs from it to the following Sunday. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;
  private long stew;
  private long soup;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
    soup = meals.quickAdd(anna, annasHome, "Soup").id();
  }

  private SlotView add(LocalDate date, SlotType type, long meal) {
    return planning.add(anna, annasHome, date, type, meal, null);
  }

  private List<Long> slotIdsOfWeek() {
    return planning.week(anna, annasHome, MONDAY).slots().stream().map(SlotView::id).toList();
  }

  @Test
  void addPlacesTheMealAfterTheMealsAlreadyInThatDayAndSlotType() {
    SlotView first = add(MONDAY, SlotType.LUNCH, stew);

    SlotView second = add(MONDAY, SlotType.LUNCH, soup);

    assertThat(first.position()).isZero();
    assertThat(second.position()).isEqualTo(1);
    assertThat(second.mealId()).isEqualTo(soup);
    assertThat(second.note()).isNull();
    assertThat(second.version()).isZero();
  }

  @Test
  void aBlankNoteIsStoredAsNoNote() {
    SlotView added = planning.add(anna, annasHome, MONDAY, SlotType.SNACK, stew, "   ");

    assertThat(added.note()).isNull();
  }

  @Test
  void theWeekListsItsSlotsByDateThenSlotTypeThenPosition() {
    SlotView dinner = add(MONDAY, SlotType.DINNER, stew);
    SlotView lunchLater = add(MONDAY.plusDays(1), SlotType.LUNCH, soup);
    SlotView breakfast = add(MONDAY, SlotType.BREAKFAST, soup);
    SlotView lunchFirst = add(MONDAY, SlotType.LUNCH, stew);
    SlotView lunchSecond = add(MONDAY, SlotType.LUNCH, soup);

    PlanWeek week = planning.week(anna, annasHome, MONDAY);

    assertThat(week.monday()).isEqualTo(MONDAY);
    assertThat(week.slots())
        .extracting(SlotView::id)
        .containsExactly(
            breakfast.id(), lunchFirst.id(), lunchSecond.id(), dinner.id(), lunchLater.id());
  }

  @Test
  void anyDayOfTheWeekGivesTheSameMondayToSundayWeek() {
    SlotView monday = add(MONDAY, SlotType.BREAKFAST, stew);
    SlotView sunday = add(MONDAY.plusDays(6), SlotType.SNACK, soup);
    add(MONDAY.plusDays(7), SlotType.BREAKFAST, soup);
    add(MONDAY.minusDays(1), SlotType.BREAKFAST, soup);

    PlanWeek fromSunday = planning.week(anna, annasHome, MONDAY.plusDays(6));

    assertThat(fromSunday.monday()).isEqualTo(MONDAY);
    assertThat(fromSunday.slots())
        .extracting(SlotView::id)
        .containsExactly(monday.id(), sunday.id());
  }

  @Test
  void anEmptyWeekHasNoSlots() {
    PlanWeek week = planning.week(anna, annasHome, MONDAY.plusWeeks(3));

    assertThat(week.monday()).isEqualTo(MONDAY.plusWeeks(3));
    assertThat(week.slots()).isEmpty();
  }

  @Test
  void moveChangesTheDayAndSlotTypeAndJoinsTheEndOfTheNewGroup() {
    SlotView waiting = add(MONDAY.plusDays(2), SlotType.DINNER, soup);
    SlotView moved = add(MONDAY, SlotType.LUNCH, stew);

    SlotView after =
        planning.move(
            anna, annasHome, moved.id(), moved.version(), MONDAY.plusDays(2), SlotType.DINNER);

    assertThat(after.date()).isEqualTo(MONDAY.plusDays(2));
    assertThat(after.slotType()).isEqualTo(SlotType.DINNER);
    assertThat(after.position()).isEqualTo(1);
    assertThat(after.version()).isEqualTo(moved.version() + 1);
    assertThat(positionOf(waiting.id()).position()).isZero();
  }

  @Test
  void moveClosesTheGapItLeavesBehind() {
    SlotView first = add(MONDAY, SlotType.LUNCH, stew);
    SlotView second = add(MONDAY, SlotType.LUNCH, soup);
    SlotView third = add(MONDAY, SlotType.LUNCH, stew);

    planning.move(anna, annasHome, first.id(), first.version(), MONDAY, SlotType.DINNER);

    SlotView secondNow = positionOf(second.id());
    SlotView thirdNow = positionOf(third.id());
    assertThat(secondNow.position()).isZero();
    assertThat(thirdNow.position()).isEqualTo(1);
  }

  @Test
  void moveToTheSameDayAndSlotTypeChangesNothingAndPublishesNothing() {
    SlotView only = add(MONDAY, SlotType.LUNCH, stew);
    int plannedBefore = events.stream(SlotPlanned.class).toList().size();

    SlotView same =
        planning.move(anna, annasHome, only.id(), only.version(), MONDAY, SlotType.LUNCH);

    assertThat(same).isEqualTo(only);
    assertThat(events.stream(SlotPlanned.class).toList()).hasSize(plannedBefore);
  }

  @Test
  void reorderMovesASlotWithinItsGroup() {
    SlotView first = add(MONDAY, SlotType.DINNER, stew);
    SlotView second = add(MONDAY, SlotType.DINNER, soup);
    SlotView third = add(MONDAY, SlotType.DINNER, stew);

    SlotView moved = planning.reorder(anna, annasHome, third.id(), third.version(), 0);

    assertThat(moved.position()).isZero();
    assertThat(positionOf(first.id()).position()).isEqualTo(1);
    assertThat(positionOf(second.id()).position()).isEqualTo(2);
    assertThat(slotIdsOfWeek()).containsExactly(third.id(), first.id(), second.id());
  }

  @Test
  void reorderClampsThePositionToTheGroup() {
    SlotView first = add(MONDAY, SlotType.SNACK, stew);
    SlotView second = add(MONDAY, SlotType.SNACK, soup);

    SlotView last = planning.reorder(anna, annasHome, first.id(), first.version(), 99);
    SlotView firstAgain = planning.reorder(anna, annasHome, first.id(), last.version(), -4);

    assertThat(last.position()).isEqualTo(1);
    assertThat(firstAgain.position()).isZero();
    assertThat(positionOf(second.id()).position()).isEqualTo(1);
  }

  @Test
  void removeTakesTheSlotOutAndClosesTheGap() {
    SlotView first = add(MONDAY, SlotType.BREAKFAST, stew);
    SlotView second = add(MONDAY, SlotType.BREAKFAST, soup);
    SlotView third = add(MONDAY, SlotType.BREAKFAST, stew);

    planning.remove(anna, annasHome, second.id(), second.version());

    assertThat(slotIdsOfWeek()).containsExactly(first.id(), third.id());
    assertThat(positionOf(third.id()).position()).isEqualTo(1);
  }

  @Test
  void editNoteSetsTheNoteAndABlankNoteClearsIt() {
    SlotView slot = add(MONDAY, SlotType.DINNER, stew);

    SlotView noted =
        planning.editNote(anna, annasHome, slot.id(), slot.version(), "  Kids eat early  ");
    SlotView cleared = planning.editNote(anna, annasHome, slot.id(), noted.version(), "  ");

    assertThat(noted.note()).isEqualTo("Kids eat early");
    assertThat(noted.version()).isEqualTo(slot.version() + 1);
    assertThat(cleared.note()).isNull();
  }

  @Test
  void aTooLongNoteIsRefusedAndNothingChanges() {
    SlotView slot = add(MONDAY, SlotType.DINNER, stew);
    String tooLong = "x".repeat(PlanRules.NOTE_MAX + 1);

    assertThatThrownBy(() -> planning.editNote(anna, annasHome, slot.id(), slot.version(), tooLong))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NOTE_TOO_LONG);
    assertThat(positionOf(slot.id()).note()).isNull();
    assertThat(positionOf(slot.id()).version()).isEqualTo(slot.version());
  }

  @Test
  void aStaleVersionOfAMoveIsRefusedBeforeAnyWrite() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    planning.editNote(anna, annasHome, slot.id(), slot.version(), "Changed by someone");

    assertThatThrownBy(() ->
            planning.move(anna, annasHome, slot.id(), slot.version(), MONDAY, SlotType.DINNER))
        .isInstanceOf(PlanConflictException.class);
    assertThat(positionOf(slot.id()).slotType()).isEqualTo(SlotType.LUNCH);
  }

  @Test
  void aStaleVersionOfAReorderIsRefused() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    planning.editNote(anna, annasHome, slot.id(), slot.version(), "Newer");

    assertThatThrownBy(() -> planning.reorder(anna, annasHome, slot.id(), slot.version(), 0))
        .isInstanceOf(PlanConflictException.class);
  }

  @Test
  void aStaleVersionOfANoteEditIsRefused() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    planning.editNote(anna, annasHome, slot.id(), slot.version(), "Newer");

    assertThatThrownBy(() -> planning.editNote(anna, annasHome, slot.id(), slot.version(), "Older"))
        .isInstanceOf(PlanConflictException.class);
  }

  @Test
  void aStaleVersionOfARemoveIsRefusedAndTheSlotStays() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    planning.editNote(anna, annasHome, slot.id(), slot.version(), "Newer");

    assertThatThrownBy(() -> planning.remove(anna, annasHome, slot.id(), slot.version()))
        .isInstanceOf(PlanConflictException.class);
    assertThat(slotIdsOfWeek()).containsExactly(slot.id());
  }

  @Test
  void aWriteThatLosesTheRaceInTheDatabaseBecomesAConflict() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    // Another transaction bumped the row after this one read it: the in-memory version is still
    // the old one, so the caller's version passes the check and the UPDATE finds no row to change.
    jdbc.update("update meal_slot set version = version + 1 where id = ?", slot.id());

    assertThatThrownBy(
            () -> planning.editNote(anna, annasHome, slot.id(), slot.version(), "Too late"))
        .isInstanceOf(PlanConflictException.class);
  }

  @Test
  void aStrangerIsRefusedByEveryMethod() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    long stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");

    assertThatThrownBy(() -> planning.week(stranger, annasHome, MONDAY))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> planning.add(stranger, annasHome, MONDAY, SlotType.LUNCH, stew, null))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() ->
            planning.move(stranger, annasHome, slot.id(), slot.version(), MONDAY, SlotType.DINNER))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> planning.reorder(stranger, annasHome, slot.id(), slot.version(), 0))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> planning.editNote(stranger, annasHome, slot.id(), slot.version(), "x"))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> planning.remove(stranger, annasHome, slot.id(), slot.version()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> planning.recentlyPlannedMealIds(stranger, annasHome, 5))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void anotherHouseholdsSlotIsNotFoundForItsOwnMembers() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);

    assertThatThrownBy(() ->
            planning.move(bela, belasHome, slot.id(), slot.version(), MONDAY, SlotType.DINNER))
        .isInstanceOf(SlotNotFoundException.class);
    assertThatThrownBy(() -> planning.reorder(bela, belasHome, slot.id(), slot.version(), 0))
        .isInstanceOf(SlotNotFoundException.class);
    assertThatThrownBy(() -> planning.editNote(bela, belasHome, slot.id(), slot.version(), "x"))
        .isInstanceOf(SlotNotFoundException.class);
    assertThatThrownBy(() -> planning.remove(bela, belasHome, slot.id(), slot.version()))
        .isInstanceOf(SlotNotFoundException.class);
    assertThat(planning.week(bela, belasHome, MONDAY).slots()).isEmpty();
    assertThat(slotIdsOfWeek()).containsExactly(slot.id());
  }

  @Test
  void aMealOfAnotherHouseholdCannotBePlanned() {
    long belasMeal = meals.quickAdd(bela, belasHome, "Pie").id();

    assertThatThrownBy(() -> planning.add(anna, annasHome, MONDAY, SlotType.SNACK, belasMeal, null))
        .isInstanceOf(MealNotFoundException.class);
    assertThat(slotIdsOfWeek()).isEmpty();
  }

  @Test
  void addAndMovePublishSlotPlannedButReorderNoteAndRemoveDoNot() {
    SlotView slot = add(MONDAY, SlotType.LUNCH, stew);
    SlotView other = add(MONDAY, SlotType.LUNCH, soup);
    assertThat(events.stream(SlotPlanned.class))
        .extracting(SlotPlanned::slotId)
        .containsExactly(slot.id(), other.id());

    SlotView moved =
        planning.move(anna, annasHome, slot.id(), slot.version(), MONDAY, SlotType.DINNER);
    // The move renumbered the sibling, so its version moved on too: use the version it has now.
    SlotView reordered =
        planning.reorder(anna, annasHome, other.id(), positionOf(other.id()).version(), 0);
    SlotView noted = planning.editNote(anna, annasHome, other.id(), reordered.version(), "Late");
    planning.remove(anna, annasHome, moved.id(), moved.version());

    assertThat(events.stream(SlotPlanned.class))
        .extracting(SlotPlanned::slotId, SlotPlanned::slotType)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple(slot.id(), SlotType.LUNCH),
            org.assertj.core.api.Assertions.tuple(other.id(), SlotType.LUNCH),
            org.assertj.core.api.Assertions.tuple(slot.id(), SlotType.DINNER));
    assertThat(noted.note()).isEqualTo("Late");
  }

  @Test
  void archivingAMealKeepsItsSlotsInThePlan() {
    SlotView slot = add(MONDAY, SlotType.DINNER, stew);

    meals.archive(anna, annasHome, stew);

    assertThat(planning.week(anna, annasHome, MONDAY).slots())
        .extracting(SlotView::mealId)
        .containsExactly(stew);
    SlotView moved =
        planning.move(
            anna, annasHome, slot.id(), slot.version(), MONDAY.plusDays(1), SlotType.DINNER);
    assertThat(moved.mealId()).isEqualTo(stew);
  }

  @Test
  void recentlyPlannedMealIdsListEachMealOnceAtItsLatestPastDate() {
    LocalDate today = LocalDate.now(clock);
    add(today.minusDays(5), SlotType.LUNCH, stew);
    add(today.minusDays(2), SlotType.LUNCH, soup);
    add(today.plusDays(3), SlotType.LUNCH, soup);
    add(today.minusDays(1), SlotType.DINNER, stew);

    List<Long> recent = planning.recentlyPlannedMealIds(anna, annasHome, 10);

    assertThat(recent).containsExactly(stew, soup);
  }

  @Test
  void recentlyPlannedMealIdsRespectTheLimit() {
    LocalDate today = LocalDate.now(clock);
    add(today.minusDays(3), SlotType.LUNCH, stew);
    add(today.minusDays(1), SlotType.LUNCH, soup);

    assertThat(planning.recentlyPlannedMealIds(anna, annasHome, 1)).containsExactly(soup);
  }

  @Test
  void recentlyPlannedMealIdsAreEmptyForAHouseholdWithoutPlans() {
    assertThat(planning.recentlyPlannedMealIds(anna, annasHome, 5)).isEmpty();
  }

  @Test
  void recentlyPlannedMealIdsRefuseALimitBelowOne() {
    assertThatThrownBy(() -> planning.recentlyPlannedMealIds(anna, annasHome, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWeekNeverShowsAnotherHouseholdsSlots() {
    add(MONDAY, SlotType.LUNCH, stew);
    long belasMeal = meals.quickAdd(bela, belasHome, "Pie").id();
    planning.add(bela, belasHome, MONDAY, SlotType.LUNCH, belasMeal, null);

    assertThat(planning.week(anna, annasHome, MONDAY).slots())
        .extracting(SlotView::mealId)
        .containsExactly(stew);
    assertThat(planning.week(bela, belasHome, MONDAY).slots())
        .extracting(SlotView::mealId)
        .containsExactly(belasMeal);
  }

  private SlotView positionOf(long slotId) {
    return planning.week(anna, annasHome, MONDAY).slots().stream()
        .filter(slot -> slot.id() == slotId)
        .findFirst()
        .orElseThrow();
  }
}
