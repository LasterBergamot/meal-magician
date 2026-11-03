package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The unchecked week reader used by share links (PLAN.md D28): only the requested household, only
 * that week, a fixed order, and a result type with nothing private in it. The meal-name reader in
 * {@code catalog} is exercised through it.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SharedWeekReaderTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private SharedWeekReader reader;
  @Autowired private PlanningService planning;
  @Autowired private OccasionService occasions;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

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

  @Test
  void anEmptyWeekHasNoMealsAndNoOccasions() {
    SharedWeek week = reader.read(annasHome, MONDAY);

    assertThat(week.monday()).isEqualTo(MONDAY);
    assertThat(week.meals()).isEmpty();
    assertThat(week.occasions()).isEmpty();
  }

  @Test
  void mealsComeInDateSlotTypeAndPositionOrder() {
    planning.add(anna, annasHome, MONDAY.plusDays(1), SlotType.BREAKFAST, stew, null);
    planning.add(anna, annasHome, MONDAY, SlotType.DINNER, soup, null);
    planning.add(anna, annasHome, MONDAY, SlotType.BREAKFAST, soup, null);
    planning.add(anna, annasHome, MONDAY, SlotType.BREAKFAST, stew, null);

    List<SharedMeal> shared = reader.read(annasHome, MONDAY).meals();

    assertThat(shared)
        .extracting(
            SharedMeal::date, SharedMeal::slotType, SharedMeal::position, SharedMeal::mealName)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(MONDAY, SlotType.BREAKFAST, 0, "Soup"),
            org.assertj.core.groups.Tuple.tuple(MONDAY, SlotType.BREAKFAST, 1, "Stew"),
            org.assertj.core.groups.Tuple.tuple(MONDAY, SlotType.DINNER, 0, "Soup"),
            org.assertj.core.groups.Tuple.tuple(MONDAY.plusDays(1), SlotType.BREAKFAST, 0, "Stew"));
  }

  @Test
  void onlyTheSevenDaysOfTheWeekAreRead() {
    planning.add(anna, annasHome, MONDAY.minusDays(1), SlotType.LUNCH, stew, null);
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);
    planning.add(anna, annasHome, MONDAY.plusDays(6), SlotType.LUNCH, stew, null);
    planning.add(anna, annasHome, MONDAY.plusDays(7), SlotType.LUNCH, stew, null);
    occasions.create(
        anna,
        annasHome,
        new OccasionDetails("Before", MONDAY.minusDays(1), OccasionType.OTHER, null));
    occasions.create(
        anna,
        annasHome,
        new OccasionDetails("After", MONDAY.plusDays(7), OccasionType.OTHER, null));

    SharedWeek week = reader.read(annasHome, MONDAY);

    assertThat(week.meals())
        .extracting(SharedMeal::date)
        .containsExactly(MONDAY, MONDAY.plusDays(6));
    assertThat(week.occasions()).isEmpty();
  }

  @Test
  void anotherHouseholdsPlanIsNeverIncluded() {
    long pie = meals.quickAdd(bela, belasHome, "Pie").id();
    planning.add(bela, belasHome, MONDAY, SlotType.LUNCH, pie, null);
    occasions.create(
        bela, belasHome, new OccasionDetails("Belas party", MONDAY, OccasionType.PARTY, null));
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, null);

    SharedWeek week = reader.read(annasHome, MONDAY);

    assertThat(week.meals()).extracting(SharedMeal::mealName).containsExactly("Stew");
    assertThat(week.occasions()).isEmpty();
  }

  @Test
  void aHouseholdWithDataOnlyInAnotherWeekGetsAnEmptyWeek() {
    planning.add(anna, annasHome, MONDAY.plusWeeks(1), SlotType.LUNCH, stew, null);

    assertThat(reader.read(annasHome, MONDAY).meals()).isEmpty();
  }

  @Test
  void occasionsComeByDateWithNameAndTypeOnly() {
    occasions.create(
        anna,
        annasHome,
        new OccasionDetails("Dinner party", MONDAY.plusDays(4), OccasionType.PARTY, "secret"));
    occasions.create(
        anna,
        annasHome,
        new OccasionDetails("Birthday", MONDAY.plusDays(2), OccasionType.BIRTHDAY, null));

    assertThat(reader.read(annasHome, MONDAY).occasions())
        .containsExactly(
            new SharedOccasion(MONDAY.plusDays(2), "Birthday", OccasionType.BIRTHDAY),
            new SharedOccasion(MONDAY.plusDays(4), "Dinner party", OccasionType.PARTY));
  }

  @Test
  void aNoteIsNotInTheResult() {
    planning.add(anna, annasHome, MONDAY, SlotType.LUNCH, stew, "allergy: nuts");

    assertThat(reader.read(annasHome, MONDAY).toString()).doesNotContain("allergy");
  }

  @Test
  void theResultTypesHaveNoIdNoteOrHouseholdField() {
    List<String> names =
        List.of(SharedWeek.class, SharedMeal.class, SharedOccasion.class).stream()
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(RecordComponent::getName)
            .toList();

    assertThat(names)
        .containsExactlyInAnyOrder(
            "monday",
            "meals",
            "occasions",
            "date",
            "slotType",
            "position",
            "mealName",
            "date",
            "name",
            "type");
  }
}
