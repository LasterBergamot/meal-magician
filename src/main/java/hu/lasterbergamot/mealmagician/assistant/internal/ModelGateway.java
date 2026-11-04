package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The <strong>only</strong> class that talks to Spring AI's {@link ChatModel} and {@link
 * EmbeddingModel}, and so the only place where household data leaves the app for Google (PLAN.md
 * D32). Having one door means the three rules below hold for every call, whatever feature makes it:
 *
 * <ol>
 *   <li><strong>The cap is checked first.</strong> A household at or over its monthly allowance is
 *       refused with {@link SpendCapExceededException} and no call is made.
 *   <li><strong>Every call has a timeout</strong> ({@code mm.ai.timeout}). A call that is too slow
 *       is abandoned with {@link ModelUnavailableException} and nothing is recorded.
 *   <li><strong>Usage is metered from what the provider reports</strong> and added to the {@link
 *       UsageLedger} after the call. Nothing about the request or the answer is logged: no prompt,
 *       no answer, no provider error text (those can echo the prompt). The class logs nothing at
 *       all.
 * </ol>
 *
 * <p>The bean exists only when {@code mm.ai.enabled=true}. While the assistant is off there is no
 * gateway, and Spring AI's own auto-configuration is switched off too (see {@code
 * application.yml}), so the app starts without Google credentials.
 */
@Component
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class ModelGateway implements AutoCloseable {

  /**
   * Rough size of a token for text, used only when the provider reports no usage at all. About four
   * characters per token is the usual rule of thumb for Latin text; Hungarian words are longer than
   * English ones, so this tends to under-count slightly, which is why it is only a fallback.
   */
  private static final int CHARS_PER_TOKEN = 4;

  private final ChatModel chatModel;
  private final EmbeddingModel embeddingModel;
  private final UsageLedger ledger;
  private final AiProperties properties;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Creates the gateway.
   *
   * @param chatModel Spring AI's chat model for Gemini on Vertex AI
   * @param embeddingModel Spring AI's embedding model
   * @param ledger the spend ledger
   * @param properties the {@code mm.ai.*} settings
   */
  public ModelGateway(
      ChatModel chatModel,
      EmbeddingModel embeddingModel,
      UsageLedger ledger,
      AiProperties properties) {
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.ledger = ledger;
    this.properties = properties;
  }

  /**
   * Asks the model once and meters the call.
   *
   * <p>Thinking tokens are billed as output but are not part of the "candidates" count, so the
   * output is taken as the larger of the reported candidate tokens and {@code total - prompt}. That
   * also counts tokens the provider adds for tool use, at the dearer output price: the ledger
   * over-counts a little rather than under-counts.
   *
   * @param householdId the household the call is made for, whose allowance is charged
   * @param prompt the messages (and options) to send; never logged
   * @return the answer text and what the call used
   * @throws SpendCapExceededException if the household has used up this month's allowance; no call
   *     was made
   * @throws ModelUnavailableException if the call failed or took longer than {@code mm.ai.timeout};
   *     nothing was recorded
   */
  public ModelReply complete(long householdId, Prompt prompt) {
    ledger.assertWithinCap(householdId);
    ChatResponse response = withTimeout(() -> chatModel.call(prompt));
    var generation = response.getResult();
    String text =
        generation == null ? "" : Objects.requireNonNullElse(generation.getOutput().getText(), "");
    Usage usage = response.getMetadata().getUsage();
    long input = tokens(usage.getPromptTokens());
    long output =
        Math.max(tokens(usage.getCompletionTokens()), tokens(usage.getTotalTokens()) - input);
    if (input == 0 && output == 0) {
      // The provider reported nothing, so estimate rather than let a call go unmetered.
      input = estimate(prompt.getContents());
      output = estimate(text);
    }
    long cost = ledger.record(householdId, input, output);
    return new ModelReply(text, input, output, cost);
  }

  /**
   * Turns one text into an embedding vector and meters the call.
   *
   * @param householdId the household the call is made for
   * @param text the text to embed; never logged
   * @return the vector, {@code mm.ai.embedding-dimensions} long
   * @throws SpendCapExceededException if the household has used up this month's allowance
   * @throws ModelUnavailableException if the call failed, timed out, or the model answered with a
   *     vector of another size (the database column is fixed to one size)
   */
  public float[] embed(long householdId, String text) {
    return embedAll(householdId, List.of(text)).getFirst();
  }

  /**
   * Turns several texts into embedding vectors in one call and meters it.
   *
   * <p>Embeddings count toward the same allowance, as input tokens. The provider's reported token
   * count is used; if it reports none, the count is estimated as one token per four characters. The
   * chat model's input price is used for them, which is dearer than Google's embedding price, so
   * again the ledger over-counts.
   *
   * @param householdId the household the call is made for
   * @param texts the texts to embed; never logged. An empty list makes no call.
   * @return one vector per text, in the same order
   * @throws SpendCapExceededException if the household has used up this month's allowance
   * @throws ModelUnavailableException if the call failed, timed out, or any vector has the wrong
   *     size
   */
  public List<float[]> embedAll(long householdId, List<String> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }
    ledger.assertWithinCap(householdId);
    EmbeddingResponse response = withTimeout(() -> embeddingModel.embedForResponse(texts));
    List<float[]> vectors = response.getResults().stream().map(e -> e.getOutput()).toList();
    if (vectors.size() != texts.size()
        || vectors.stream().anyMatch(v -> v.length != properties.embeddingDimensions())) {
      throw new ModelUnavailableException(
          "the embedding does not have the expected size of " + properties.embeddingDimensions());
    }
    long reported = tokens(response.getMetadata().getUsage().getTotalTokens());
    long input = reported > 0 ? reported : texts.stream().mapToLong(ModelGateway::estimate).sum();
    ledger.record(householdId, input, 0);
    return vectors;
  }

  /** Stops the threads that run the calls; Spring calls it when the application closes. */
  @Override
  public void close() {
    executor.close();
  }

  /**
   * Runs a call on its own thread and gives up after {@code mm.ai.timeout}. The provider client has
   * its own retries, so without this a bad moment at Google could hold a request thread for
   * minutes. Giving up interrupts the call; whether the HTTP client notices is up to it, so the
   * answer, if one still arrives, is dropped.
   */
  private <T> T withTimeout(Callable<T> call) {
    Future<T> future = executor.submit(call);
    try {
      return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new ModelUnavailableException("the call timed out after " + properties.timeout());
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new ModelUnavailableException("the call was interrupted");
    } catch (ExecutionException e) {
      // Only the kind of failure: the provider's message may quote the prompt.
      throw new ModelUnavailableException(
          "the call failed (" + e.getCause().getClass().getSimpleName() + ")");
    }
  }

  private static long tokens(Integer reported) {
    return Objects.requireNonNullElse(reported, 0);
  }

  private static long estimate(String text) {
    return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
  }
}
