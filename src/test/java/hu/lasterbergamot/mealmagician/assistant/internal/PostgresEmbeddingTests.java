package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The embedding table and its SQL on the real database (pgvector): the household filter, the
 * archive filter, the cascade and the composite foreign key. The model is a fake that gives each
 * text a vector from a lookup table; the beans of the assistant are off in this context ({@code
 * mm.ai.enabled=false}), so the store and the index are built by hand on the real datasource. Each
 * test rolls back.
 */
@Transactional
@Tag("integration")
class PostgresEmbeddingTests extends AbstractPostgresIntegrationTest {

  private static final int DIMS = 768;

  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JdbcClient jdbcClient;

  private EmbeddingStore store;
  private long anna;
  private long bela;
  private long annasHome;
  private long belasHome;

  @BeforeEach
  void setUp() {
    store = new EmbeddingStore(jdbcClient);
    anna = HouseholdTestSupport.insertUser(jdbc, "pg-emb-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "pg-emb-bela");
    annasHome = households.create(anna, "Emb anna").id();
    belasHome = households.create(bela, "Emb bela").id();
  }

  /** A unit-ish vector pointing mostly along one axis: closeness to {@code axis(n)} is by n. */
  private static float[] axis(int n, float weight) {
    float[] v = new float[DIMS];
    v[n] = 1f;
    v[(n + 1) % DIMS] = weight;
    return v;
  }

  private long meal(long user, long home, String name) {
    return meals.quickAdd(user, home, name).id();
  }

  private long count(long mealId) {
    return jdbc.queryForObject(
        "select count(*) from meal_embedding where meal_id = ?", Long.class, mealId);
  }

  @Test
  void searchReturnsOnlyTheHouseholdsMealsEvenWhenAnotherHouseholdMatchesBetter() {
    long annasSoup = meal(anna, annasHome, "Soup");
    long annasPie = meal(anna, annasHome, "Pie");
    long belasPerfect = meal(bela, belasHome, "Perfect match");
    store.upsert(annasHome, annasSoup, "h1", "m", axis(5, 0.9f));
    store.upsert(annasHome, annasPie, "h2", "m", axis(6, 0.0f));
    store.upsert(belasHome, belasPerfect, "h3", "m", axis(5, 0.0f));

    // The query is exactly Bela's meal; Anna's best is still only her own.
    var forAnna = store.nearest(annasHome, axis(5, 0.0f), 10);
    var forBela = store.nearest(belasHome, axis(5, 0.0f), 10);

    assertThat(forAnna).containsExactly(annasSoup, annasPie);
    assertThat(forBela).containsExactly(belasPerfect);
  }

  @Test
  void nearestRespectsTheLimitAndOrdersByDistance() {
    long near = meal(anna, annasHome, "Near");
    long middle = meal(anna, annasHome, "Middle");
    long far = meal(anna, annasHome, "Far");
    store.upsert(annasHome, far, "h", "m", axis(9, 0.0f));
    store.upsert(annasHome, middle, "h", "m", axis(5, 1.0f));
    store.upsert(annasHome, near, "h", "m", axis(5, 0.0f));

    assertThat(store.nearest(annasHome, axis(5, 0.0f), 3)).containsExactly(near, middle, far);
    assertThat(store.nearest(annasHome, axis(5, 0.0f), 1)).containsExactly(near);
  }

  @Test
  void anArchivedMealIsNeverReturnedEvenBeforeItsRowIsCleanedUp() {
    long keep = meal(anna, annasHome, "Keep");
    long gone = meal(anna, annasHome, "Gone");
    store.upsert(annasHome, keep, "h", "m", axis(1, 0.5f));
    store.upsert(annasHome, gone, "h", "m", axis(1, 0.0f));
    meals.archive(anna, annasHome, gone);

    assertThat(store.nearest(annasHome, axis(1, 0.0f), 10)).containsExactly(keep);
  }

  @Test
  void upsertReplacesTheRowAndHashesListThem() {
    long soup = meal(anna, annasHome, "Soup");
    store.upsert(annasHome, soup, "old", "m1", axis(1, 0.0f));
    store.upsert(annasHome, soup, "new", "m2", axis(2, 0.0f));

    assertThat(store.hashes(annasHome)).containsExactly(java.util.Map.entry(soup, "new"));
    assertThat(store.hashes(belasHome)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select model || ':' || dims from meal_embedding where meal_id = ?",
                String.class,
                soup))
        .isEqualTo("m2:768");
  }

  @Test
  void deleteRemovesOnlyTheHouseholdsRowsAndAnEmptyCollectionDoesNothing() {
    long annasSoup = meal(anna, annasHome, "Soup");
    long belasSoup = meal(bela, belasHome, "Soup");
    store.upsert(annasHome, annasSoup, "h", "m", axis(1, 0.0f));
    store.upsert(belasHome, belasSoup, "h", "m", axis(1, 0.0f));

    store.delete(annasHome, Set.of());
    assertThat(count(annasSoup)).isEqualTo(1);

    // Anna's household asks to delete Bela's meal id: nothing happens to it.
    store.delete(annasHome, Set.of(annasSoup, belasSoup));
    assertThat(count(annasSoup)).isZero();
    assertThat(count(belasSoup)).isEqualTo(1);
  }

  @Test
  void deletingTheMealRemovesItsEmbeddingByCascade() {
    long soup = meal(anna, annasHome, "Soup");
    store.upsert(annasHome, soup, "h", "m", axis(1, 0.0f));

    jdbc.update("delete from meal where id = ?", soup);

    assertThat(count(soup)).isZero();
  }

  @Test
  void theCompositeForeignKeyRefusesARowWithTheWrongHousehold() {
    long annasSoup = meal(anna, annasHome, "Soup");

    assertThatThrownBy(() -> store.upsert(belasHome, annasSoup, "h", "m", axis(1, 0.0f)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void syncAndSearchWorkEndToEndForOneHouseholdOnly() {
    long soup = meal(anna, annasHome, "Lentil soup");
    long pie = meal(anna, annasHome, "Apple pie");
    meal(bela, belasHome, "Bela's stew");
    var gateway = mock(ModelGateway.class);
    when(gateway.embedAll(anyLong(), anyList()))
        .thenAnswer(call -> {
          List<String> texts = call.getArgument(1);
          return texts.stream()
              .map(t -> t.contains("soup") ? axis(1, 0.0f) : axis(20, 0.0f))
              .toList();
        });
    when(gateway.embed(anyLong(), anyString())).thenReturn(axis(1, 0.1f));
    var properties =
        new AiProperties(
            true,
            new BigDecimal("5.00"),
            new BigDecimal("1.65"),
            new BigDecimal("9.90"),
            DIMS,
            Duration.ofSeconds(60));
    var index = new EmbeddingIndex(meals, households, gateway, store, properties, "model-x");

    assertThat(index.sync(anna, annasHome)).isEqualTo(2);
    assertThat(index.sync(anna, annasHome)).isZero();

    assertThat(index.search(anna, annasHome, "something with lentils", 5))
        .containsExactly(soup, pie);
    assertThat(store.hashes(belasHome)).isEmpty();
    assertThat(index.search(bela, belasHome, "something with lentils", 5)).isEmpty();

    meals.archive(anna, annasHome, pie);
    assertThat(index.sync(anna, annasHome)).isZero();
    assertThat(store.hashes(annasHome)).containsOnlyKeys(soup);
  }

  @Test
  void aVectorIsWrittenInPgvectorTextForm() {
    assertThat(EmbeddingStore.toVectorText(new float[] {0.5f, -1f, 2f}))
        .isEqualTo("[0.5,-1.0,2.0]");
    assertThat(EmbeddingStore.toVectorText(new float[0])).isEqualTo("[]");
  }
}
