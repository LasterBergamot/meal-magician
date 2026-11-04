package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The assistant's meal search: finds the household's meals that fit a request, by meaning, without
 * sending the whole library to the model (PLAN.md D32, #158).
 *
 * <p><strong>How it stays current without events or a scheduler.</strong> Cloud Run throttles the
 * CPU once a response has been sent, so background work is unreliable, and nothing may wake the app
 * on a timer (D11). Instead {@link #sync} runs <em>inside</em> the assistant's own request, before
 * it searches: it builds the text of every active meal ({@link MealDocument}), hashes it together
 * with the model id and vector size, and embeds only the meals whose stored hash differs or is
 * missing. A second sync with nothing changed makes no model call at all. A changed model id or
 * size changes every hash, so everything is embedded again; a size the database column cannot hold
 * is refused by {@link ModelGateway}.
 *
 * <p>{@link #search} deliberately does not call {@link #sync}: the caller decides when to pay for
 * one (usually once at the start of a request).
 *
 * <p><strong>Safety.</strong> Every query is scoped by the household ({@link EmbeddingStore}); the
 * meals come from {@link MealService}, which checks that the person belongs to the household. Meal
 * text goes to the embedding model through {@link ModelGateway} only, which meters it against the
 * household's allowance. Nothing here logs meal text, queries or vectors.
 */
@Component
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class EmbeddingIndex {

  /** How many documents go to the model in one call. */
  static final int BATCH_SIZE = 50;

  private final MealService meals;
  private final HouseholdService households;
  private final ModelGateway gateway;
  private final EmbeddingStore store;
  private final String model;
  private final String modelKey;

  /**
   * One lock per household, so two simultaneous assistant requests of the same household do not
   * embed the same meals twice. The map is never trimmed: a household adds one small entry and
   * there are only a few households.
   */
  private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

  /**
   * Creates the index.
   *
   * @param meals the catalog's public service, the source of the meals
   * @param households does the membership check for {@link #search}
   * @param gateway the only door to the embedding model
   * @param store the SQL for {@code meal_embedding}
   * @param properties the assistant settings, for the vector size
   * @param model the embedding model id ({@code spring.ai.google.genai.embedding.text.model}); it
   *     is part of every hash, so changing it re-embeds everything
   */
  public EmbeddingIndex(
      MealService meals,
      HouseholdService households,
      ModelGateway gateway,
      EmbeddingStore store,
      AiProperties properties,
      @Value("${spring.ai.google.genai.embedding.text.model:}") String model) {
    this.meals = meals;
    this.households = households;
    this.gateway = gateway;
    this.store = store;
    this.model = model;
    this.modelKey = model + "@" + properties.embeddingDimensions();
  }

  /**
   * Brings the household's stored embeddings in line with its active meals: rows of archived or
   * removed meals are deleted, and meals that are new or whose text (or model) changed are embedded
   * in batches of {@value #BATCH_SIZE} and stored. Each batch is stored as soon as it arrives, so a
   * failure part-way (for example the spend cap) keeps the work already done; the next sync
   * continues from there.
   *
   * <p>Holds the household's lock for the whole run, so a second caller waits and then finds
   * nothing to do.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household to index
   * @return how many meals were embedded by this call; {@code 0} when everything was current
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household has
   *     used up this month's allowance
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model call
   *     failed
   */
  public int sync(long userId, long householdId) {
    ReentrantLock lock = locks.computeIfAbsent(householdId, id -> new ReentrantLock());
    lock.lock();
    try {
      List<MealView> active =
          meals.search(userId, householdId, new MealSearch("", null, null, false));
      Map<Long, String> stored = store.hashes(householdId);

      Set<Long> activeIds = new HashSet<>();
      List<MealView> todo = new ArrayList<>();
      Map<Long, String> documents = new HashMap<>();
      Map<Long, String> hashes = new HashMap<>();
      for (MealView meal : active) {
        activeIds.add(meal.id());
        String text = MealDocument.of(meal);
        String hash = MealDocument.hash(text, modelKey);
        if (!hash.equals(stored.get(meal.id()))) {
          todo.add(meal);
          documents.put(meal.id(), text);
          hashes.put(meal.id(), hash);
        }
      }

      // Cheap and needs no model, so it goes first: a failure below must not leave archived meals
      // searchable.
      Set<Long> gone = new HashSet<>(stored.keySet());
      gone.removeAll(activeIds);
      store.delete(householdId, gone);

      for (int from = 0; from < todo.size(); from += BATCH_SIZE) {
        List<MealView> batch = todo.subList(from, Math.min(from + BATCH_SIZE, todo.size()));
        List<float[]> vectors =
            gateway.embedAll(householdId, batch.stream().map(m -> documents.get(m.id())).toList());
        for (int i = 0; i < batch.size(); i++) {
          MealView meal = batch.get(i);
          store.upsert(householdId, meal.id(), hashes.get(meal.id()), model, vectors.get(i));
        }
      }
      return todo.size();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Finds the household's meals closest in meaning to a request. Does <strong>not</strong> sync
   * first. Only the household's own, non-archived meals can come back, however well another
   * household's meals match.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household to search
   * @param query what the person asked for; text from a person or a model, treated as data. It is
   *     cut to {@value MealDocument#MAX_CHARS} characters.
   * @param limit the most meals to return; below 1 returns nothing
   * @return meal ids, best match first; empty when the query is blank, the limit is below 1 or
   *     nothing is indexed. In those first two cases no model call is made.
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household has
   *     used up this month's allowance
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model call
   *     failed
   */
  public List<Long> search(long userId, long householdId, String query, int limit) {
    households.require(householdId, userId);
    if (query == null || query.isBlank() || limit < 1) {
      return List.of();
    }
    float[] vector = gateway.embed(householdId, MealDocument.cap(query.strip()));
    return store.nearest(householdId, vector, limit);
  }
}
