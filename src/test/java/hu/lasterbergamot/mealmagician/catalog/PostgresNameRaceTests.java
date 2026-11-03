package hu.lasterbergamot.mealmagician.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.internal.NameWriter;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two people saving the same new tag or ingredient at the same moment, on real Postgres, where a
 * failed insert aborts the transaction. Not {@code @Transactional}: the point is two separate
 * transactions. The overlap is forced, not left to luck: the first transaction inserts the name and
 * stays open while the second meal is saved, so the second insert must wait for it and then find
 * the row instead of failing.
 */
@Tag("integration")
class PostgresNameRaceTests extends AbstractPostgresIntegrationTest {

  @Autowired private MealService meals;
  @Autowired private NameWriter writer;
  @Autowired private HouseholdService households;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long home;

  @AfterEach
  void cleanUp() {
    // The household owns the meals, tags and ingredients (ON DELETE CASCADE).
    jdbc.update("delete from household where id = ?", home);
    jdbc.update("delete from app_user where id = ?", anna);
  }

  private void startHousehold(String name) {
    anna = HouseholdTestSupport.insertUser(jdbc, name);
    home = households.create(anna, name + " home").id();
  }

  /**
   * Runs {@code firstWriter} inside a transaction that stays open until the second meal has been
   * started, then lets it commit. Returns once the second save has finished.
   */
  private void raceAgainstOpenTransaction(Runnable firstWriter, MealDetails second)
      throws Exception {
    CountDownLatch inserted = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    CompletableFuture<Void> first =
        CompletableFuture.runAsync(() ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                  firstWriter.run();
                  inserted.countDown();
                  await(commit);
                }));
    assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<MealView> other =
        CompletableFuture.supplyAsync(() -> meals.create(anna, home, second));
    // Give the second save time to reach its insert and block on the first one's row.
    Thread.sleep(500);
    commit.countDown();

    first.get(10, TimeUnit.SECONDS);
    MealView saved = other.get(10, TimeUnit.SECONDS);
    assertThat(saved.id()).isPositive();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void twoSavesOfTheSameNewIngredientBothSucceedWithOneRow() throws Exception {
    startHousehold("pg-race-ingredient");

    raceAgainstOpenTransaction(
        () -> writer.ingredient(home, "tojas", "tojas"),
        new MealDetails("Omelette", null, null, null, null, List.of("Tojás")));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from ingredient where household_id = ? and name_key = 'tojas'",
                Integer.class,
                home))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from meal_ingredient where household_id = ?", Integer.class, home))
        .isEqualTo(1);
  }

  @Test
  void twoSavesOfTheSameNewTagBothSucceedWithOneRow() throws Exception {
    startHousehold("pg-race-tag");

    raceAgainstOpenTransaction(
        () -> writer.tag(home, "vegan", "vegan"),
        new MealDetails("Lentil soup", null, null, List.of("Vegan")));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from tag where household_id = ? and name_key = 'vegan'",
                Integer.class,
                home))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from meal_tag where household_id = ?", Integer.class, home))
        .isEqualTo(1);
  }
}
