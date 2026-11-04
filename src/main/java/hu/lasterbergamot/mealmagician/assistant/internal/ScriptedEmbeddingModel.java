package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * The embedding half of the {@code fake-ai} profile: every text becomes the same all-zero vector.
 * Nothing searches with it, because {@link ScriptedEmbeddingIndex} replaces the real meal search;
 * the bean exists only so that {@link ModelGateway} can be built without a Google client.
 */
class ScriptedEmbeddingModel implements EmbeddingModel {

  private final int dimensions;

  /**
   * Creates the model.
   *
   * @param dimensions the vector size, {@code mm.ai.embedding-dimensions}
   */
  ScriptedEmbeddingModel(int dimensions) {
    this.dimensions = dimensions;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<Embedding> embeddings = new ArrayList<>();
    for (int i = 0; i < request.getInstructions().size(); i++) {
      embeddings.add(new Embedding(new float[dimensions], i));
    }
    return new EmbeddingResponse(embeddings);
  }

  @Override
  public float[] embed(Document document) {
    return new float[dimensions];
  }

  @Override
  public int dimensions() {
    return dimensions;
  }
}
