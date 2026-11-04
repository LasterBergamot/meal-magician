package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;

class ScriptedEmbeddingModelTest {

  private final ScriptedEmbeddingModel model = new ScriptedEmbeddingModel(4);

  @Test
  void everyTextGetsAVectorOfTheConfiguredSize() {
    var response = model.call(new EmbeddingRequest(List.of("a", "b"), null));

    assertThat(response.getResults()).hasSize(2);
    assertThat(response.getResults().get(1).getOutput()).hasSize(4);
    assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
    assertThat(model.call(new EmbeddingRequest(List.of(), null)).getResults()).isEmpty();
  }

  @Test
  void aDocumentAndTheModelReportTheSameSize() {
    assertThat(model.embed(new Document("text"))).hasSize(4);
    assertThat(model.dimensions()).isEqualTo(4);
  }
}
