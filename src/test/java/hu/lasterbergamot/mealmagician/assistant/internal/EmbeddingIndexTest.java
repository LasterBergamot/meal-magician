package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The index with a fake gateway (a Mockito mock that returns one vector per text) and an in-memory
 * store, so every decision of {@code sync} and {@code search} is checked without a database or a
 * model. The SQL itself is tested on Postgres in {@link PostgresEmbeddingTests}.
 */
class EmbeddingIndexTest {

  private static final long USER = 7L;
  private static final long HOME = 11L;
  private static final int DIMENSIONS = 3;

  private final MealService meals = mock(MealService.class);
  private final HouseholdService households = mock(HouseholdService.class);
  private final ModelGateway gateway = mock(ModelGateway.class);
  private final MemoryStore store = new MemoryStore();
  private final List<MealView> library = new ArrayList<>();

  @BeforeEach
  void stubCatalogAndGateway() {
    when(meals.search(anyLong(), anyLong(), any(MealSearch.class))).thenReturn(library);
    when(gateway.embedAll(anyLong(), anyList()))
        .thenAnswer(call -> {
          List<?> texts = call.getArgument(1);
          return texts.stream().map(t -> new float[] {1f, 2f, 3f}).toList();
        });
    when(gateway.embed(anyLong(), anyString())).thenReturn(new float[] {0f, 1f, 0f});
  }

  private EmbeddingIndex index(String model) {
    var properties =
        new AiProperties(
            true,
            new BigDecimal("5.00"),
            new BigDecimal("1.65"),
            new BigDecimal("9.90"),
            DIMENSIONS,
            Duration.ofSeconds(60));
    return new EmbeddingIndex(meals, households, gateway, store, properties, model);
  }

  private static MealView meal(long id, String name, String... ingredients) {
    return new MealView(
        id, name, null, null, List.of(), List.of(), false, 0L, List.of(ingredients));
  }

  @Test
  void anEmptyIndexEmbedsEveryActiveMealInOneBatch() {
    library.addAll(List.of(meal(1, "Soup"), meal(2, "Pasta"), meal(3, "Salad")));

    int embedded = index("m").sync(USER, HOME);

    assertThat(embedded).isEqualTo(3);
    verify(gateway, times(1)).embedAll(anyLong(), anyList());
    assertThat(store.rows).containsOnlyKeys(1L, 2L, 3L);
    assertThat(store.models.values()).containsOnly("m");
  }

  @Test
  void aSecondSyncWithNoChangesMakesNoGatewayCall() {
    library.addAll(List.of(meal(1, "Soup"), meal(2, "Pasta")));
    var index = index("m");
    index.sync(USER, HOME);

    int embedded = index.sync(USER, HOME);

    assertThat(embedded).isZero();
    verify(gateway, times(1)).embedAll(anyLong(), anyList());
  }

  @Test
  void editingOneMealsIngredientsEmbedsOnlyThatMeal() {
    library.addAll(List.of(meal(1, "Soup", "lencse"), meal(2, "Pasta")));
    var index = index("m");
    index.sync(USER, HOME);
    library.set(0, meal(1, "Soup", "lencse", "répa"));

    int embedded = index.sync(USER, HOME);

    assertThat(embedded).isEqualTo(1);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
    verify(gateway, times(2)).embedAll(anyLong(), texts.capture());
    assertThat(texts.getAllValues().get(1)).hasSize(1).first().asString().contains("répa");
  }

  @Test
  void anArchivedOrRemovedMealLosesItsRowAndNothingIsEmbedded() {
    library.addAll(List.of(meal(1, "Soup"), meal(2, "Pasta")));
    var index = index("m");
    index.sync(USER, HOME);
    library.remove(1);

    int embedded = index.sync(USER, HOME);

    assertThat(embedded).isZero();
    assertThat(store.rows).containsOnlyKeys(1L);
    verify(gateway, times(1)).embedAll(anyLong(), anyList());
  }

  @Test
  void aChangedModelIdReEmbedsEverything() {
    library.addAll(List.of(meal(1, "Soup"), meal(2, "Pasta")));
    index("model-a").sync(USER, HOME);

    int embedded = index("model-b").sync(USER, HOME);

    assertThat(embedded).isEqualTo(2);
    assertThat(store.models.values()).containsOnly("model-b");
  }

  @Test
  void aChangedDimensionReEmbedsEverything() {
    library.add(meal(1, "Soup"));
    index("m").sync(USER, HOME);
    var wider =
        new EmbeddingIndex(
            meals,
            households,
            gateway,
            store,
            new AiProperties(
                true,
                new BigDecimal("5.00"),
                new BigDecimal("1.65"),
                new BigDecimal("9.90"),
                DIMENSIONS + 1,
                Duration.ofSeconds(60)),
            "m");

    assertThat(wider.sync(USER, HOME)).isEqualTo(1);
  }

  @Test
  void aHundredAndTwentyMealsGoInBatchesOfFiftyFiftyAndTwenty() {
    IntStream.rangeClosed(1, 120).forEach(i -> library.add(meal(i, "Meal " + i)));

    int embedded = index("m").sync(USER, HOME);

    assertThat(embedded).isEqualTo(120);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
    verify(gateway, times(3)).embedAll(anyLong(), texts.capture());
    assertThat(texts.getAllValues()).extracting(List::size).containsExactly(50, 50, 20);
    assertThat(store.rows).hasSize(120);
  }

  @Test
  void aFailureKeepsTheBatchesAlreadyStoredAndTheNextSyncContinues() {
    IntStream.rangeClosed(1, 60).forEach(i -> library.add(meal(i, "Meal " + i)));
    var calls = new int[1];
    when(gateway.embedAll(anyLong(), anyList()))
        .thenAnswer(call -> {
          if (++calls[0] == 2) {
            throw new ModelUnavailableException("boom");
          }
          List<?> texts = call.getArgument(1);
          return texts.stream().map(t -> new float[] {1f, 2f, 3f}).toList();
        });
    var index = index("m");

    assertThatThrownBy(() -> index.sync(USER, HOME)).isInstanceOf(ModelUnavailableException.class);
    assertThat(store.rows).hasSize(50);
    assertThat(index.sync(USER, HOME)).isEqualTo(10);
    assertThat(store.rows).hasSize(60);
  }

  @Test
  void aStrangerCannotSync() {
    when(meals.search(anyLong(), anyLong(), any(MealSearch.class)))
        .thenThrow(new HouseholdNotFoundException());

    assertThatThrownBy(() -> index("m").sync(USER, HOME))
        .isInstanceOf(HouseholdNotFoundException.class);
    verifyNoInteractions(gateway);
  }

  @Test
  void twoSimultaneousSyncsEmbedEachMealOnce() throws Exception {
    library.addAll(List.of(meal(1, "Soup"), meal(2, "Pasta"), meal(3, "Salad")));
    when(gateway.embedAll(anyLong(), anyList()))
        .thenAnswer(call -> {
          Thread.sleep(150);
          List<?> texts = call.getArgument(1);
          return texts.stream().map(t -> new float[] {1f, 2f, 3f}).toList();
        });
    var index = index("m");
    var go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        results.add(
            pool.submit(() -> {
              go.await();
              return index.sync(USER, HOME);
            }));
      }
      go.countDown();
      int total = 0;
      for (Future<Integer> result : results) {
        total += result.get();
      }
      assertThat(total).isEqualTo(3);
    } finally {
      pool.shutdownNow();
    }

    verify(gateway, times(1)).embedAll(anyLong(), anyList());
  }

  @Test
  void searchEmbedsTheQueryAndReturnsTheStoresIds() {
    store.nearest = List.of(5L, 4L);

    var ids = index("m").search(USER, HOME, "  something light  ", 5);

    assertThat(ids).containsExactly(5L, 4L);
    verify(households).require(HOME, USER);
    verify(gateway).embed(HOME, "something light");
    verify(gateway, never()).embedAll(anyLong(), anyList());
    assertThat(store.lastLimit).isEqualTo(5);
  }

  @Test
  void searchDoesNotSyncAndCutsALongQuery() {
    library.add(meal(1, "Soup"));

    index("m").search(USER, HOME, "q".repeat(5_000), 3);

    verify(meals, never()).search(anyLong(), anyLong(), any(MealSearch.class));
    verify(gateway).embed(HOME, "q".repeat(MealDocument.MAX_CHARS));
  }

  @Test
  void aBlankQueryOrNoLimitCostsNothing() {
    var index = index("m");

    assertThat(index.search(USER, HOME, "   ", 5)).isEmpty();
    assertThat(index.search(USER, HOME, null, 5)).isEmpty();
    assertThat(index.search(USER, HOME, "soup", 0)).isEmpty();
    verifyNoInteractions(gateway);
  }

  @Test
  void aStrangerCannotSearch() {
    doThrow(new HouseholdNotFoundException()).when(households).require(HOME, USER);

    assertThatThrownBy(() -> index("m").search(USER, HOME, "soup", 5))
        .isInstanceOf(HouseholdNotFoundException.class);
    verifyNoInteractions(gateway);
  }

  /** The table in memory: what the SQL does, keyed by meal. */
  private static final class MemoryStore extends EmbeddingStore {

    final Map<Long, String> rows = new ConcurrentHashMap<>();
    final Map<Long, String> models = new ConcurrentHashMap<>();
    List<Long> nearest = List.of();
    int lastLimit;

    MemoryStore() {
      super(null);
    }

    @Override
    public Map<Long, String> hashes(long householdId) {
      return Map.copyOf(rows);
    }

    @Override
    public void upsert(long householdId, long mealId, String hash, String model, float[] vector) {
      rows.put(mealId, hash);
      models.put(mealId, model);
    }

    @Override
    public void delete(long householdId, Collection<Long> mealIds) {
      mealIds.forEach(rows::remove);
      mealIds.forEach(models::remove);
    }

    @Override
    public List<Long> nearest(long householdId, float[] query, int limit) {
      lastLimit = limit;
      return nearest;
    }
  }
}
