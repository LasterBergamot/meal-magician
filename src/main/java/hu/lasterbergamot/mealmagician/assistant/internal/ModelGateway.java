package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li><strong>Every call has a timeout</strong> ({@code mm.ai.timeout}), and a whole request has
 *       a total budget ({@code mm.ai.request-timeout}, 120 s by default) shared by all its tool
 *       rounds. A call that is too slow, or a request that has used up its budget, is abandoned
 *       with {@link ModelUnavailableException}; a call that did not finish records nothing.
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

  /**
   * The most times one {@link #complete} asks the model. Each tool round is one more call, so this
   * bounds both the tool rounds and the cost of a model that keeps asking for tools. A normal
   * answer needs two to four: the model looks things up once or twice, then answers.
   */
  static final int MAX_ROUNDS = 12;

  private final ChatModel chatModel;
  private final EmbeddingModel embeddingModel;
  private final UsageLedger ledger;
  private final AiProperties properties;
  private final ToolCallingManager toolCalling;
  private final LongSupplier nanoClock;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Creates the gateway.
   *
   * @param chatModel Spring AI's chat model for Gemini on Vertex AI
   * @param embeddingModel Spring AI's embedding model
   * @param ledger the spend ledger
   * @param properties the {@code mm.ai.*} settings
   * @param toolCalling runs the tools the model asks for; Spring AI's own auto-configured manager
   */
  @Autowired
  public ModelGateway(
      ChatModel chatModel,
      EmbeddingModel embeddingModel,
      UsageLedger ledger,
      AiProperties properties,
      ToolCallingManager toolCalling) {
    this(chatModel, embeddingModel, ledger, properties, toolCalling, System::nanoTime);
  }

  /**
   * Creates the gateway with its own clock; a package-private seam so a test can move time without
   * sleeping. The public constructor passes {@link System#nanoTime()}.
   *
   * @param chatModel Spring AI's chat model for Gemini on Vertex AI
   * @param embeddingModel Spring AI's embedding model
   * @param ledger the spend ledger
   * @param properties the {@code mm.ai.*} settings
   * @param toolCalling runs the tools the model asks for
   * @param nanoClock a monotonic clock in nanoseconds, used to measure the request's total time
   */
  ModelGateway(
      ChatModel chatModel,
      EmbeddingModel embeddingModel,
      UsageLedger ledger,
      AiProperties properties,
      ToolCallingManager toolCalling,
      LongSupplier nanoClock) {
    this.nanoClock = nanoClock;
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.ledger = ledger;
    this.properties = properties;
    this.toolCalling = toolCalling;
  }

  /**
   * Asks the model and, while it asks for tools, runs them and asks again; every call is metered.
   *
   * <p>Spring AI 2's Gemini model does <em>not</em> run tools itself: {@code ChatModel.call}
   * returns the model's tool requests with no text and leaves the rest to the caller (#183). This
   * method is that caller, so every feature that gives the model tools (drafts, slot suggestions,
   * chat, menu help) gets the loop from the one door: call, run the requested tools with Spring
   * AI's {@link ToolCallingManager} on the calling thread, add the results to the conversation,
   * call again, until a round has no tool request. A model that never stops asking is cut off after
   * {@link #MAX_ROUNDS} calls. The tools stay on the options of the prompt for every round.
   *
   * <p>Every round is recorded in the ledger as its own call, so a failure in a later round does
   * not lose the cost of the earlier ones; the reply adds the rounds up and its text is the last
   * round's. Thinking tokens are billed as output but are not part of the "candidates" count, so
   * the output of a round is taken as the larger of the reported candidate tokens and {@code total
   * - prompt}. That also counts tokens the provider adds for tool use, at the dearer output price:
   * the ledger over-counts a little rather than under-counts.
   *
   * <p>The cap is checked once, before the first call. Each call has the timeout {@code
   * mm.ai.timeout}, and the whole request has the budget {@code mm.ai.request-timeout}: before each
   * round the time already used is taken off the budget, and the round's call may take at most the
   * smaller of the per-call timeout and what is left, so the last round cannot run past the budget.
   * The empty-message check is done on the prompt as given, not on the rounds after it: the
   * assistant message that carries tool requests and the tool results legitimately have no text.
   *
   * @param householdId the household the call is made for, whose allowance is charged
   * @param prompt the messages (and options, with the tools) to send; never logged
   * @return the last answer's text and what all the calls used together
   * @throws SpendCapExceededException if the household has used up this month's allowance; no call
   *     was made
   * @throws ModelUnavailableException if a message of the prompt has no text (no call was made; the
   *     reason names the message's kind and position, never its text), if a call failed or took
   *     longer than {@code mm.ai.timeout}, if the whole request took longer than {@code
   *     mm.ai.request-timeout} (the reason holds the configured limit, never prompt text), if a
   *     requested tool could not be run, or if the model was still asking for tools after {@link
   *     #MAX_ROUNDS} calls; the calls made so far stay recorded
   */
  public ModelReply complete(long householdId, Prompt prompt) {
    ledger.assertWithinCap(householdId);
    refuseEmptyMessages(prompt);
    Prompt current = prompt;
    long input = 0;
    long output = 0;
    long cost = 0;
    long started = nanoClock.getAsLong();
    for (int round = 0; round < MAX_ROUNDS; round++) {
      Duration remaining = properties.requestTimeout().minusNanos(nanoClock.getAsLong() - started);
      if (remaining.compareTo(Duration.ZERO) <= 0) {
        throw requestTooLong();
      }
      Prompt sent = current;
      boolean budgetLimits = remaining.compareTo(properties.timeout()) < 0;
      ChatResponse response =
          withTimeout(
              () -> chatModel.call(sent),
              budgetLimits ? remaining : properties.timeout(),
              budgetLimits ? this::requestTooLong : this::callTimedOut);
      var generation = response.getResult();
      String text =
          generation == null
              ? ""
              : Objects.requireNonNullElse(generation.getOutput().getText(), "");
      Usage usage = response.getMetadata().getUsage();
      long roundInput = tokens(usage.getPromptTokens());
      long roundOutput =
          Math.max(
              tokens(usage.getCompletionTokens()), tokens(usage.getTotalTokens()) - roundInput);
      if (roundInput == 0 && roundOutput == 0) {
        // The provider reported nothing, so estimate rather than let a call go unmetered.
        roundInput = estimate(sent.getContents());
        roundOutput = estimate(text);
      }
      cost += ledger.record(householdId, roundInput, roundOutput);
      input += roundInput;
      output += roundOutput;
      if (!response.hasToolCalls()) {
        return new ModelReply(text, input, output, cost);
      }
      current = withToolResults(current, response);
    }
    throw new ModelUnavailableException(
        "the model was still asking for tools after " + MAX_ROUNDS + " calls");
  }

  private ModelUnavailableException requestTooLong() {
    return new ModelUnavailableException(
        "the request took longer than " + properties.requestTimeout());
  }

  private ModelUnavailableException callTimedOut() {
    return new ModelUnavailableException("the call timed out after " + properties.timeout());
  }

  /**
   * Runs the tools a response asks for and builds the prompt for the next round: the conversation
   * so far, the model's tool request and the results, with the same options (so the same tools).
   *
   * @param prompt the prompt the response answered
   * @param response a response that has tool requests
   * @return the prompt for the next call
   * @throws ModelUnavailableException if the tools could not be run (for example the model named a
   *     tool the prompt does not carry); the reason has only the kind of failure, as a tool's
   *     message may quote what the member typed
   */
  private Prompt withToolResults(Prompt prompt, ChatResponse response) {
    try {
      var result = toolCalling.executeToolCalls(prompt, response);
      return new Prompt(result.conversationHistory(), prompt.getOptions());
    } catch (RuntimeException e) {
      throw new ModelUnavailableException(
          "the tool call failed (" + e.getClass().getSimpleName() + ")");
    }
  }

  /**
   * Refuses a prompt that has a message without text, before anything is sent. Gemini answers such
   * a request with HTTP 400 ("must include at least one parts field"), which would only show up as
   * a bare provider failure; naming the message here points at the code that built it (#183). The
   * application sends text messages only (the tool requests and results of later rounds are added
   * by {@link #complete} after this check), so any message that is empty or white space is a bug in
   * the caller.
   *
   * @param prompt the prompt about to be sent
   * @throws ModelUnavailableException if a message has no text; the reason holds the kind and the
   *     position of the message, never its text
   */
  private static void refuseEmptyMessages(Prompt prompt) {
    List<Message> messages = prompt.getInstructions();
    for (int i = 0; i < messages.size(); i++) {
      String text = messages.get(i).getText();
      if (text == null || text.isBlank()) {
        throw new ModelUnavailableException(
            "the prompt has an empty " + messages.get(i).getMessageType() + " message at " + i);
      }
    }
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
    EmbeddingResponse response =
        withTimeout(
            () -> embeddingModel.embedForResponse(texts), properties.timeout(), this::callTimedOut);
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
   * Runs a call on its own thread and gives up after {@code limit}. The provider client has its own
   * retries, so without this a bad moment at Google could hold a request thread for minutes. Giving
   * up interrupts the call; whether the HTTP client notices is up to it, so the answer, if one
   * still arrives, is dropped.
   *
   * @param call the call to run
   * @param limit how long to wait: the per-call timeout, or less when the request's total budget is
   *     nearly used up
   * @param onTimeout makes the failure to throw when the limit passes; it says which limit it was
   */
  private <T> T withTimeout(
      Callable<T> call, Duration limit, Supplier<ModelUnavailableException> onTimeout) {
    Future<T> future = executor.submit(call);
    try {
      return future.get(limit.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw onTimeout.get();
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
