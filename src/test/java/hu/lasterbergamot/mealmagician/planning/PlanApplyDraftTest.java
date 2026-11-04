package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealNotFoundException;
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
 * Writing an assistant draft into a week ({@code PlanningService.applyDraft}) against the dev
 * profile's H2. Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class PlanApplyDraftTest {

  /** A Monday in ISO week 45. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

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
    anna = HouseholdTestSupport.insertUser(jdbc, "ad-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "ad-bela");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
    soup = meals.quickAdd(anna, annasHome, "Soup").id();
    cake = meals.quickAdd(anna, annasHome, "Cake").id();
  }

  private List<SlotView> week() {
    return planning.week(anna, annasHome, MONDAY).slots();
  }

  private List<DraftSlot> three() {
    return List.of(
        new DraftSlot(MONDAY, SlotType.LUNCH, stew),
        new DraftSlot(MONDAY.plusDays(1), SlotType.DINNER, soup),
        new DraftSlot(MONDAY.plusDays(6), SlotType.SNACK, cake));
  }

  private void assertProblem(Runnable action, PlanProblem problem) {
    assertThatThrownBy(action::run)
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(problem);
  }

  @Test
  void mergeAppendsToAWeekThatAlreadyHasTwoSlots() {
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, soup, null);
    planning.add(anna, annasHome, MONDAY.plusDays(2), SlotType.DINNER, soup, null);
    long before = events.stream(SlotPlanned.class).count();

    int written = planning.applyDraft(anna, annasHome, MONDAY, three(), CopyMode.MERGE);

    assertThat(written).isEqualTo(3);
    assertThat(week()).hasSize(5);
    assertThat(events.stream(SlotPlanned.class).count() - before).isEqualTo(3);
    assertThat(
            week().stream()
                .filter(s -> s.date().equals(MONDAY) && s.slotType() == SlotType.LUNCH)
                .map(SlotView::position))
        .containsExactly(0, 1);
  }

  @Test
  void replaceEmptiesTheWholeWeekFirst() {
    planning.add(anna, annasHome, MONDAY.plusDays(3), SlotType.BREAKFAST, soup, null);
    planning.add(anna, annasHome, MONDAY.plusDays(4), SlotType.DINNER, soup, null);

    int written = planning.applyDraft(anna, annasHome, MONDAY, three(), CopyMode.REPLACE);

    assertThat(written).isEqualTo(3);
    assertThat(week()).extracting(SlotView::mealId).containsExactlyInAnyOrder(stew, soup, cake);
    assertThat(week()).hasSize(3);
  }

  @Test
  void anyDayOfTheWeekNamesTheWeek() {
    int written = planning.applyDraft(anna, annasHome, MONDAY.plusDays(3), three(), CopyMode.MERGE);

    assertThat(written).isEqualTo(3);
    assertThat(week()).hasSize(3);
  }

  @Test
  void aDateOutsideTheWeekIsRefusedAndNothingIsWritten() {
    List<DraftSlot> draft =
        List.of(
            new DraftSlot(MONDAY, SlotType.LUNCH, stew),
            new DraftSlot(MONDAY.plusDays(7), SlotType.LUNCH, soup));

    assertProblem(
        () -> planning.applyDraft(anna, annasHome, MONDAY, draft, CopyMode.MERGE),
        PlanProblem.OUTSIDE_WEEK);
    assertThat(week()).isEmpty();
  }

  @Test
  void aDateBeforeTheWeekIsRefused() {
    List<DraftSlot> draft = List.of(new DraftSlot(MONDAY.minusDays(1), SlotType.LUNCH, stew));

    assertProblem(
        () -> planning.applyDraft(anna, annasHome, MONDAY, draft, CopyMode.MERGE),
        PlanProblem.OUTSIDE_WEEK);
  }

  @Test
  void anEmptyDraftIsRefusedSoAReplaceNeverWipesTheWeek() {
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, soup, null);

    assertProblem(
        () -> planning.applyDraft(anna, annasHome, MONDAY, List.of(), CopyMode.REPLACE),
        PlanProblem.NOTHING_TO_COPY);
    assertThat(week()).hasSize(1);
  }

  @Test
  void anArchivedMealIsRefusedAndNothingIsWritten() {
    meals.archive(anna, annasHome, soup);
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);

    assertProblem(
        () -> planning.applyDraft(anna, annasHome, MONDAY, three(), CopyMode.REPLACE),
        PlanProblem.MEAL_ARCHIVED);
    assertThat(week()).hasSize(1);
  }

  @Test
  void aMealOfAnotherHouseholdIsNotFound() {
    long foreign = meals.quickAdd(bela, belasHome, "Foreign").id();
    List<DraftSlot> draft = List.of(new DraftSlot(MONDAY, SlotType.LUNCH, foreign));

    assertThatThrownBy(() -> planning.applyDraft(anna, annasHome, MONDAY, draft, CopyMode.MERGE))
        .isInstanceOf(MealNotFoundException.class);
    assertThat(week()).isEmpty();
  }

  @Test
  void aNonMemberGetsNotFound() {
    assertThatThrownBy(() -> planning.applyDraft(bela, annasHome, MONDAY, three(), CopyMode.MERGE))
        .isInstanceOf(HouseholdNotFoundException.class);
  }
}
