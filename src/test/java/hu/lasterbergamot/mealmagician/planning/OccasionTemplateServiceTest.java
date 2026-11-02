package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occasion templates (save from an occasion, list, find, use, delete), against the dev profile's
 * H2. Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OccasionTemplateServiceTest {

  private static final LocalDate XMAS = LocalDate.of(2026, 12, 24);

  @Autowired private OccasionTemplateService templates;
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

  /** An occasion with stew then soup on its menu. */
  private OccasionView occasionWithMenu() {
    long id =
        occasions
            .create(
                anna,
                annasHome,
                new OccasionDetails("Christmas", XMAS, OccasionType.HOLIDAY, "Fish"))
            .id();
    occasions.addItem(anna, annasHome, id, stew);
    return occasions.addItem(anna, annasHome, id, soup);
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

  @Test
  void savingCopiesTheTypeNotesAndMenuInOrder() {
    OccasionView occasion = occasionWithMenu();

    OccasionTemplateView saved =
        templates.saveFromOccasion(anna, annasHome, occasion.id(), "Karácsony");

    assertThat(saved.name()).isEqualTo("Karácsony");
    assertThat(saved.type()).isEqualTo(OccasionType.HOLIDAY);
    assertThat(saved.notes()).isEqualTo("Fish");
    assertThat(saved.menu())
        .extracting(MenuItemView::position, MenuItemView::mealId)
        .containsExactly(tuple(0, stew), tuple(1, soup));
  }

  @Test
  void anEmptyMenuABlankNameOrAForeignOccasionIsRefused() {
    long empty =
        occasions
            .create(anna, annasHome, new OccasionDetails("Empty", XMAS, OccasionType.PARTY, null))
            .id();
    OccasionView full = occasionWithMenu();

    assertProblem(
        () -> templates.saveFromOccasion(anna, annasHome, empty, "x"), PlanProblem.NOTHING_TO_COPY);
    assertProblem(
        () -> templates.saveFromOccasion(anna, annasHome, full.id(), " "),
        PlanProblem.NAME_REQUIRED);
    assertThatThrownBy(() -> templates.saveFromOccasion(bela, belasHome, full.id(), "x"))
        .isInstanceOf(OccasionNotFoundException.class);
    assertThat(count("occasion_template")).isZero();
  }

  @Test
  void theListIsSortedByNameIgnoringCaseAndCountsItems() {
    OccasionView occasion = occasionWithMenu();
    templates.saveFromOccasion(anna, annasHome, occasion.id(), "b");
    templates.saveFromOccasion(anna, annasHome, occasion.id(), "A");

    assertThat(templates.list(anna, annasHome))
        .extracting(
            OccasionTemplateSummary::name,
            OccasionTemplateSummary::type,
            OccasionTemplateSummary::itemCount)
        .containsExactly(tuple("A", OccasionType.HOLIDAY, 2), tuple("b", OccasionType.HOLIDAY, 2));
    assertThat(templates.list(bela, belasHome)).isEmpty();
  }

  @Test
  void anotherHouseholdsTemplateIsNotFound() {
    long id = templates.saveFromOccasion(anna, annasHome, occasionWithMenu().id(), "Mine").id();

    assertThatThrownBy(() -> templates.find(bela, belasHome, id))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThatThrownBy(() -> templates.use(bela, belasHome, id, XMAS))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThatThrownBy(() -> templates.delete(bela, belasHome, id))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThat(templates.find(anna, annasHome, id).name()).isEqualTo("Mine");
  }

  @Test
  void usingATemplateCreatesAnOccasionWithACopyOfTheMenu() {
    OccasionView source = occasionWithMenu();
    long id = templates.saveFromOccasion(anna, annasHome, source.id(), "Karácsony").id();
    LocalDate next = LocalDate.of(2027, 12, 24);

    OccasionView used = templates.use(anna, annasHome, id, next);

    assertThat(used.id()).isNotEqualTo(source.id());
    assertThat(used.name()).isEqualTo("Karácsony");
    assertThat(used.type()).isEqualTo(OccasionType.HOLIDAY);
    assertThat(used.notes()).isEqualTo("Fish");
    assertThat(used.date()).isEqualTo(next);
    assertThat(used.menu()).extracting(MenuItemView::mealId).containsExactly(stew, soup);
    assertThat(used.menu())
        .extracting(MenuItemView::id)
        .doesNotContainAnyElementsOf(source.menu().stream().map(MenuItemView::id).toList());
    assertThat(occasions.find(anna, annasHome, used.id()).menu()).hasSize(2);
  }

  @Test
  void usingATemplateWithoutADateCreatesNothing() {
    long id = templates.saveFromOccasion(anna, annasHome, occasionWithMenu().id(), "T").id();

    assertProblem(() -> templates.use(anna, annasHome, id, null), PlanProblem.DATE_REQUIRED);
    assertThat(count("occasion")).isEqualTo(1);
  }

  @Test
  void deletingATemplateLeavesTheOccasionsMadeFromIt() {
    long id = templates.saveFromOccasion(anna, annasHome, occasionWithMenu().id(), "T").id();
    long used = templates.use(anna, annasHome, id, XMAS.plusYears(1)).id();

    templates.delete(anna, annasHome, id);

    assertThatThrownBy(() -> templates.find(anna, annasHome, id))
        .isInstanceOf(TemplateNotFoundException.class);
    assertThat(occasions.find(anna, annasHome, used).name()).isEqualTo("T");
    // The list query flushes the pending delete, so the database cascade has run when we count.
    assertThat(templates.list(anna, annasHome)).isEmpty();
    assertThat(count("occasion_template_item")).isZero();
  }

  @Test
  void aStrangerIsRefusedByEveryMethod() {
    OccasionView occasion = occasionWithMenu();
    long id = templates.saveFromOccasion(anna, annasHome, occasion.id(), "T").id();
    long stranger = HouseholdTestSupport.insertUser(jdbc, "stranger");

    assertThatThrownBy(() -> templates.saveFromOccasion(stranger, annasHome, occasion.id(), "x"))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.list(stranger, annasHome))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.find(stranger, annasHome, id))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.use(stranger, annasHome, id, XMAS))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> templates.delete(stranger, annasHome, id))
        .isInstanceOf(HouseholdNotFoundException.class);
  }
}
