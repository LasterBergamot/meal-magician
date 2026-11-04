package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The SQL behind the embedding index: the only class that reads or writes {@code meal_embedding}
 * (V14). It uses {@link JdbcClient} rather than JPA because the {@code vector} column and the
 * {@code <=>} operator have no JPA mapping.
 *
 * <p><strong>Every statement is filtered by {@code household_id}</strong>, and every value goes in
 * as a bound parameter; no SQL is built from input. The table exists only on Postgres, so this bean
 * exists only while the assistant is on ({@code mm.ai.enabled=true}), which production and the
 * {@code postgres} profile require anyway.
 */
@Repository
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class EmbeddingStore {

  private final JdbcClient jdbc;

  /**
   * Creates the store.
   *
   * @param jdbc Spring's fluent JDBC client on the application's datasource
   */
  public EmbeddingStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The content hash stored for each meal of the household.
   *
   * @param householdId the household
   * @return meal id to content hash; empty when nothing is indexed yet
   */
  public Map<Long, String> hashes(long householdId) {
    Map<Long, String> result = new HashMap<>();
    jdbc.sql("SELECT meal_id, content_hash FROM meal_embedding WHERE household_id = :h")
        .param("h", householdId)
        .query((rs, row) -> result.put(rs.getLong("meal_id"), rs.getString("content_hash")))
        .list();
    return result;
  }

  /**
   * Inserts or replaces the embedding of one meal.
   *
   * @param householdId the household the meal belongs to; the composite foreign key makes the
   *     database refuse a row whose household differs from the meal's
   * @param mealId the meal
   * @param hash the content hash of the text that was embedded
   * @param model the model id
   * @param vector the embedding, exactly 768 numbers (the column is fixed to that)
   */
  public void upsert(long householdId, long mealId, String hash, String model, float[] vector) {
    jdbc.sql(
            """
            INSERT INTO meal_embedding
                (meal_id, household_id, content_hash, model, dims, embedding, updated_at)
            VALUES (:meal, :h, :hash, :model, :dims, CAST(:v AS vector), now())
            ON CONFLICT (meal_id) DO UPDATE SET
                household_id = EXCLUDED.household_id,
                content_hash = EXCLUDED.content_hash,
                model = EXCLUDED.model,
                dims = EXCLUDED.dims,
                embedding = EXCLUDED.embedding,
                updated_at = EXCLUDED.updated_at
            """)
        .param("meal", mealId)
        .param("h", householdId)
        .param("hash", hash)
        .param("model", model)
        .param("dims", vector.length)
        .param("v", toVectorText(vector))
        .update();
  }

  /**
   * Removes the embeddings of some meals of the household. A meal id that belongs to another
   * household matches nothing.
   *
   * @param householdId the household
   * @param mealIds the meals whose rows go; an empty collection does nothing
   */
  public void delete(long householdId, Collection<Long> mealIds) {
    if (mealIds.isEmpty()) {
      return;
    }
    jdbc.sql("DELETE FROM meal_embedding WHERE household_id = :h AND meal_id IN (:ids)")
        .param("h", householdId)
        .param("ids", mealIds)
        .update();
  }

  /**
   * The household's meals closest to a query vector, nearest first. Archived meals are never
   * returned, even if their row has not been cleaned up yet.
   *
   * @param householdId the household; another household's rows are never read, however well they
   *     match
   * @param query the query's embedding
   * @param limit the most meals to return
   * @return meal ids ordered by cosine distance, at most {@code limit}
   */
  public List<Long> nearest(long householdId, float[] query, int limit) {
    return jdbc.sql(
            """
            SELECT e.meal_id
            FROM meal_embedding e
            JOIN meal m ON m.id = e.meal_id AND m.household_id = e.household_id
            WHERE e.household_id = :h AND NOT m.archived
            ORDER BY e.embedding <=> CAST(:q AS vector)
            LIMIT :n
            """)
        .param("h", householdId)
        .param("q", toVectorText(query))
        .param("n", limit)
        .query(Long.class)
        .list();
  }

  /**
   * Writes a vector in pgvector's text form, {@code [0.1,0.2,…]}. The driver hands a bound
   * parameter to Postgres as text, so the SQL casts it with {@code CAST(:v AS vector)}.
   */
  static String toVectorText(float[] vector) {
    StringBuilder text = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(vector[i]);
    }
    return text.append(']').toString();
  }
}
