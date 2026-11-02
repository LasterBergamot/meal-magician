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
 * Plan templates (save, list, find, rename, delete, apply), against the dev profile's H2. Each test
 * runs in a transaction that is rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class PlanTemplateServiceTest {

  /** A Monday in ISO week 45. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private PlanTemplateService templates;
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

  private List<SlotView> day(LocalDate date) {
    return planning.week(anna, annasHome, date).slots().stream()
        .filter(slot -> slot.date().equals(date))
        .toList();
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

  private PlanTemplateView saveDay(String name) {
    return templates.save(anna, annasHome, TemplateScope.DAY, MONDAY, name);
  }

  @Test
  void savingADayKeepsTheTrimmedNameAndTheItemsInWeekOrder() {
    add(MONDAY, SlotType.DINNER, soup);
    add(MONDAY, SlotType.BREAKFAST, stew);
    add(MONDAY.plusDays(1), SlotType.LUNCH, stew);

    PlanTemplateView saved = saveDay(" Hétfő ");

    assertThat(saved.name()).isEqualTo("Hétfő");
    assertThat(saved.scope()).isEqualTo(TemplateScope.DAY);
    assertThat(saved.version()).isZero();
    assertThat(saved.items())
        .extracting(
            PlanTemplateItemView::dayOffset,
            PlanTemplateItemView::slotType,
            PlanTemplateItemView::mealId)
        .containsExactly(tuple(0, SlotType.BREAKFAST, stew), tuple(0, SlotType.DINNER, soup));
  }

  @Test
  void savingAWeekRemembersTheWeekdayOffsets() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(MONDAY.plusDays(6), SlotType.DINNER, soup);
    add(MONDAY.plusDays(7), SlotType.DINNER, soup);

    PlanTemplateView saved =
        templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY.plusDays(2), "Hét");

    assertThat(saved.items()).extracting(PlanTemplateItemView::dayOffset).containsExactly(0, 6);
  }

  @Test
  void twoMealsInOneGroupKeepTheirPlaces() {
    add(MONDAY, SlotType.LUNCH, stew);
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, soup, "n");

    PlanTemplateView saved = saveDay("Lunches");

    assertThat(saved.items())
        .extracting(PlanTemplateItemView::position, PlanTemplateItemView::note)
        .containsExactly(tuple(0, null), tuple(1, "n"));
  }

  @Test
  void aBadNameWritesNothing() {
    add(MONDAY, SlotType.LUNCH, stew);

    assertProblem(() -> saveDay("  "), PlanProblem.NAME_REQUIRED);
    assertProblem(() -> saveDay("x".repeat(101)), PlanProblem.NAME_TOO_LONG);
    assertThat(count("plan_template")).isZero();
  }

  @Test
  void anEmptyDayCannotBeSaved() {
    assertProblem(() -> saveDay("Empty"), PlanProblem.NOTHING_TO_COPY);
    assertThat(count("plan_template")).isZero();
  }

  @Test
  void theListIsSortedByNameIgnoringCaseAndCountsItems() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(MONDAY, SlotType.DINNER, soup);
    saveDay("b");
    templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "A");
    saveDay("c");

    List<PlanTemplateSummary> list = templates.list(anna, annasHome);

    assertThat(list)
        .extracting(
            PlanTemplateSummary::name, PlanTemplateSummary::scope, PlanTemplateSummary::itemCount)
        .containsExactly(
            tuple("A", TemplateScope.WEEK, 2),
            tuple("b", TemplateScope.DAY, 2),
            tuple("c", TemplateScope.DAY, 2));
    assertThat(templates.list(bela, belasHome)).isEmpty();
  }

  @Test
  void anotherHouseholdsTemplateIsNotFound() {
    add(MONDAY, SlotType.LUNCH, stew);
    long id = saveDay("Mine").id();

    assertThatThrownBy(() -> templates.find(bela, belasHome, id))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThatThrownBy(() -> templates.rename(bela, belasHome, id, 0, "x"))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThatThrownBy(() -> templates.delete(bela, belasHome, id, 0))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThatThrownBy(() -> templates.apply(bela, belasHome, id, MONDAY, CopyMode.MERGE))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThat(templates.find(anna, annasHome, id).name()).isEqualTo("Mine");
  }

  @Test
  void renamingChangesOnlyTheNameAndBumpsTheVersion() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Old");

    PlanTemplateView renamed =
        templates.rename(anna, annasHome, saved.id(), saved.version(), " New ");

    assertThat(renamed.name()).isEqualTo("New");
    assertThat(renamed.version()).isEqualTo(saved.version() + 1);
    assertThat(renamed.items()).hasSize(1);
  }

  @Test
  void aStaleRenameIsRefusedAndTheNameStays() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Old");
    templates.rename(anna, annasHome, saved.id(), saved.version(), "Newer");

    assertThatThrownBy(
            () -> templates.rename(anna, annasHome, saved.id(), saved.version(), "Older"))
        .isInstanceOf(TemplateConflictException.class);
    assertThat(templates.find(anna, annasHome, saved.id()).name()).isEqualTo("Newer");
  }

  @Test
  void aRenameThatLosesTheRaceBecomesAConflict() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Old");
    jdbc.update("update plan_template set version = version + 1 where id = ?", saved.id());

    assertThatThrownBy(() -> templates.rename(anna, annasHome, saved.id(), saved.version(), "x"))
        .isInstanceOf(TemplateConflictException.class);
  }

  @Test
  void aBlankRenameIsRefused() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Old");

    assertProblem(
        () -> templates.rename(anna, annasHome, saved.id(), saved.version(), " "),
        PlanProblem.NAME_REQUIRED);
  }

  @Test
  void deletingRemovesTheTemplateAndItsItems() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Gone");

    templates.delete(anna, annasHome, saved.id(), saved.version());

    assertThatThrownBy(() -> templates.find(anna, annasHome, saved.id()))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from plan_template_item where template_id = ?",
                Integer.class,
                saved.id()))
        .isZero();
  }

  @Test
  void aStaleDeleteIsRefused() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Keep");
    templates.rename(anna, annasHome, saved.id(), saved.version(), "Kept");

    assertThatThrownBy(() -> templates.delete(anna, annasHome, saved.id(), saved.version()))
        .isInstanceOf(TemplateConflictException.class);
    assertThat(count("plan_template")).isEqualTo(1);
  }

  @Test
  void applyingADayInMergeModeAppendsAfterTheMealsThere() {
    add(MONDAY, SlotType.LUNCH, stew);
    long id = saveDay("Day").id();
    LocalDate thursday = MONDAY.plusDays(3);
    add(thursday, SlotType.LUNCH, soup);

    int written = templates.apply(anna, annasHome, id, thursday, CopyMode.MERGE);

    assertThat(written).isEqualTo(1);
    assertThat(day(thursday)).extracting(SlotView::mealId).containsExactly(soup, stew);
    assertThat(events.stream(SlotPlanned.class).filter(e -> e.date().equals(thursday)).count())
        .isEqualTo(2);
  }

  @Test
  void applyingADayInReplaceModeMakesTheDayEqualToTheTemplate() {
    add(MONDAY, SlotType.LUNCH, stew);
    long id = saveDay("Day").id();
    LocalDate thursday = MONDAY.plusDays(3);
    add(thursday, SlotType.LUNCH, soup);
    add(thursday, SlotType.SNACK, soup);

    templates.apply(anna, annasHome, id, thursday, CopyMode.REPLACE);

    assertThat(day(thursday))
        .extracting(SlotView::slotType, SlotView::mealId, SlotView::position)
        .containsExactly(tuple(SlotType.LUNCH, stew, 0));
  }

  @Test
  void applyingAWeekPlacesTheMealsOnTheirWeekdays() {
    add(MONDAY, SlotType.LUNCH, stew);
    add(MONDAY.plusDays(6), SlotType.DINNER, soup);
    long id = templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "Week").id();
    LocalDate week47 = MONDAY.plusWeeks(2);

    int written = templates.apply(anna, annasHome, id, week47.plusDays(2), CopyMode.MERGE);

    assertThat(written).isEqualTo(2);
    assertThat(day(week47)).extracting(SlotView::mealId).containsExactly(stew);
    assertThat(day(week47.plusDays(6))).extracting(SlotView::mealId).containsExactly(soup);
  }

  @Test
  void applyingAWeekInReplaceModeEmptiesTheOtherSlotsOfThatWeek() {
    add(MONDAY, SlotType.LUNCH, stew);
    long id = templates.save(anna, annasHome, TemplateScope.WEEK, MONDAY, "Week").id();
    LocalDate week47 = MONDAY.plusWeeks(2);
    add(week47.plusDays(4), SlotType.SNACK, soup);

    templates.apply(anna, annasHome, id, week47, CopyMode.REPLACE);

    assertThat(day(week47.plusDays(4))).isEmpty();
    assertThat(day(week47)).extracting(SlotView::mealId).containsExactly(stew);
  }

  @Test
  void aStrangerIsRefusedByEveryMethod() {
    add(MONDAY, SlotType.LUNCH, stew);
    PlanTemplateView saved = saveDay("Mine");
    long stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");

    assertThatThrownBy(() -> templates.save(stranger, annasHome, TemplateScope.DAY, MONDAY, "x"))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.list(stranger, annasHome))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.find(stranger, annasHome, saved.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.rename(stranger, annasHome, saved.id(), 0, "x"))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.delete(stranger, annasHome, saved.id(), 0))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(
            () -> templates.apply(stranger, annasHome, saved.id(), MONDAY, CopyMode.MERGE))
        .isInstanceOf(HouseholdNotFoundException.class);
  }
}
