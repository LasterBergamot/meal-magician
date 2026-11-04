package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.assistant.DraftStatus;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Accepting is one transaction: if the plan refuses the draft, the claim that closed it is rolled
 * back too, so the draft stays open and can be fixed. This needs real commits and the real
 * transaction proxy, so the test is not {@code @Transactional}, registers the (normally switched
 * off) service as a bean, and removes its own rows afterwards.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(DraftAcceptRollbackTest.Config.class)
class DraftAcceptRollbackTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  /** The draft service as a bean; the model side is not needed to accept. */
  @TestConfiguration
  static class Config {

    @Bean
    DraftService draftService(
        DraftStore store,
        DraftRepository drafts,
        DraftItemRepository items,
        MealService meals,
        PlanningService planning,
        HouseholdService households) {
      return new DraftService(store, drafts, items, null, meals, planning, households);
    }
  }

  @Autowired private DraftService service;
  @Autowired private DraftRepository drafts;
  @Autowired private DraftItemRepository items;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long home;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from assistant_draft where household_id = ?", home);
    jdbc.update("delete from meal_slot where household_id = ?", home);
    jdbc.update("delete from household where id = ?", home);
    jdbc.update("delete from app_user where id = ?", anna);
  }

  @Test
  void aRefusedPlanWriteLeavesTheDraftOpen() {
    anna = HouseholdTestSupport.insertUser(jdbc, "rb-anna");
    home = households.create(anna, "Rollback").id();
    long stew = meals.quickAdd(anna, home, "Stew").id();
    long draftId =
        drafts.save(new DraftEntity(home, anna, MONDAY, "x", "[]", "s", Instant.now())).getId();
    items.save(DraftItemEntity.library(draftId, home, MONDAY, SlotType.LUNCH, 0, stew));
    meals.archive(anna, home, stew);

    assertThatThrownBy(() -> service.accept(anna, home, draftId, CopyMode.MERGE))
        .isInstanceOfSatisfying(
            InvalidPlanException.class,
            e -> assertThat(e.problem()).isEqualTo(PlanProblem.MEAL_ARCHIVED));

    assertThat(service.draft(anna, home, draftId).status()).isEqualTo(DraftStatus.OPEN);
  }
}
