package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The meal search of the {@code fake-ai} profile. The real {@link EmbeddingIndex} needs Postgres,
 * pgvector and a Google embedding model; this one returns the household's first active meals in the
 * catalog's order, which is all the scripted model needs. It still goes through {@link
 * MealService}, so it is household-scoped and checks membership like the real one.
 */
@Component
@Profile("fake-ai")
class ScriptedEmbeddingIndex extends EmbeddingIndex {

  private final MealService catalog;

  /**
   * Creates the index.
   *
   * @param meals the catalog's public service
   * @param households does the membership check
   * @param gateway the only door to the model (unused here, the superclass needs it)
   * @param store the SQL for {@code meal_embedding} (unused here, the superclass needs it)
   * @param properties the assistant settings
   * @param model the embedding model id (unused here, the superclass needs it)
   */
  ScriptedEmbeddingIndex(
      MealService meals,
      HouseholdService households,
      ModelGateway gateway,
      EmbeddingStore store,
      AiProperties properties,
      @Value("${spring.ai.google.genai.embedding.text.model:}") String model) {
    super(meals, households, gateway, store, properties, model);
    this.catalog = meals;
  }

  @Override
  public int sync(long userId, long householdId) {
    return 0;
  }

  @Override
  public List<Long> search(long userId, long householdId, String query, int limit) {
    return catalog.search(userId, householdId, new MealSearch("", null, null, false)).stream()
        .limit(limit)
        .map(MealView::id)
        .toList();
  }
}
