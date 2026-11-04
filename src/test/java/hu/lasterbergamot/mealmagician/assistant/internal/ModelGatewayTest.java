package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

/**
 * The gateway with fake models: no Google, no credentials, no database. The ledger is a mock so the
 * test can say exactly what the gateway asked of it.
 */
class ModelGatewayTest {

  private static final String PROMPT = "Plan dinners for the Szabo family, who love paprikas";
  private static final String ANSWER = "Monday: paprikas csirke with nokedli";
  private static final int DIMENSIONS = 4;

  private final ChatModel chat = mock(ChatModel.class);
  private final EmbeddingModel embeddings = mock(EmbeddingModel.class);
  private final UsageLedger ledger = mock(UsageLedger.class);
  private ModelGateway gateway;
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

  private static AiProperties properties(Duration timeout) {
    return properties(timeout, Duration.ofSeconds(120));
  }

  private static AiProperties properties(Duration timeout, Duration requestTimeout) {
    return new AiProperties(
        true,
        new BigDecimal("5.00"),
        new BigDecimal("1.65"),
        new BigDecimal("9.90"),
        DIMENSIONS,
        timeout,
        requestTimeout,
        "food-and-plan");
  }

  @BeforeEach
  void gateway() {
    gateway =
        new ModelGateway(
            chat,
            embeddings,
            ledger,
            properties(Duration.ofSeconds(5)),
            ToolCallingManager.builder().build());
    when(ledger.record(anyLong(), anyLong(), anyLong())).thenReturn(7L);
    logs.start();
    root.addAppender(logs);
  }

  @AfterEach
  void stop() {
    root.detachAppender(logs);
    gateway.close();
  }

  private static ChatResponse answer(String text, DefaultUsage usage) {
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(text))),
        ChatResponseMetadata.builder().usage(usage).build());
  }

  private static EmbeddingResponse vectors(int count, int size, DefaultUsage usage) {
    var results = new java.util.ArrayList<Embedding>();
    for (int i = 0; i < count; i++) {
      results.add(new Embedding(new float[size], i));
    }
    return new EmbeddingResponse(results, new EmbeddingResponseMetadata("fake", usage));
  }

  @Test
  void completeReturnsTheTextAndUsageAndRecordsIt() {
    when(chat.call(any(Prompt.class))).thenReturn(answer(ANSWER, new DefaultUsage(100, 50, 150)));

    ModelReply reply = gateway.complete(42L, new Prompt(PROMPT));

    assertThat(reply).isEqualTo(new ModelReply(ANSWER, 100, 50, 7L));
    verify(ledger).assertWithinCap(42L);
    verify(ledger).record(42L, 100, 50);
  }

  @Test
  void thinkingTokensAreBilledAsOutput() {
    // 100 in, 50 candidates, 180 total: the other 30 are thinking tokens.
    when(chat.call(any(Prompt.class))).thenReturn(answer(ANSWER, new DefaultUsage(100, 50, 180)));

    ModelReply reply = gateway.complete(1L, new Prompt(PROMPT));

    assertThat(reply.outputTokens()).isEqualTo(80);
    verify(ledger).record(1L, 100, 80);
  }

  @Test
  void aCallWithNoReportedUsageIsEstimatedNotFree() {
    when(chat.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("abcdefgh")))));

    ModelReply reply = gateway.complete(1L, new Prompt("abcd"));

    assertThat(reply.inputTokens()).isEqualTo(1);
    assertThat(reply.outputTokens()).isEqualTo(2);
    verify(ledger).record(1L, 1, 2);
  }

  @Test
  void aReportedOutputWithoutInputIsStillUsedAsReported() {
    when(chat.call(any(Prompt.class))).thenReturn(answer(ANSWER, new DefaultUsage(0, 5, 5)));

    ModelReply reply = gateway.complete(1L, new Prompt(PROMPT));

    assertThat(reply.inputTokens()).isZero();
    assertThat(reply.outputTokens()).isEqualTo(5);
  }

  @Test
  void anEmptyResultGivesEmptyText() {
    when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

    assertThat(gateway.complete(1L, new Prompt("abcd")).text()).isEmpty();
  }

  /** A tool the fake model asks for; counts its runs so a test sees that the gateway ran it. */
  public static final class Lookup {
    final java.util.concurrent.atomic.AtomicInteger runs =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Answers a lookup.
     *
     * @param what what to look up
     * @return a fixed result that includes the argument
     */
    @Tool(description = "Looks something up")
    public String lookup(String what) {
      runs.incrementAndGet();
      return "found-" + what;
    }
  }

  private static ChatResponse toolRequest(String name, DefaultUsage usage) {
    var call = new AssistantMessage.ToolCall("call-1", "function", name, "{\"what\":\"paprika\"}");
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build())),
        ChatResponseMetadata.builder().usage(usage).build());
  }

  private static Prompt promptWith(Lookup tool) {
    return new Prompt(
        List.of(new UserMessage(PROMPT)),
        ToolCallingChatOptions.builder().toolCallbacks(ToolCallbacks.from(tool)).build());
  }

  @Test
  void aToolRequestIsRunAndItsResultGoesBackToTheModelInTheNextCall() {
    var tool = new Lookup();
    when(chat.call(any(Prompt.class)))
        .thenReturn(toolRequest("lookup", new DefaultUsage(100, 10, 110)))
        .thenReturn(answer(ANSWER, new DefaultUsage(150, 20, 170)));

    ModelReply reply = gateway.complete(1L, promptWith(tool));

    assertThat(reply.text()).isEqualTo(ANSWER);
    assertThat(tool.runs).hasValue(1);
    var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    verify(chat, org.mockito.Mockito.times(2)).call(prompts.capture());
    var second = prompts.getAllValues().get(1).getInstructions();
    assertThat(second).hasSize(3);
    assertThat(second.get(1)).isInstanceOf(AssistantMessage.class);
    assertThat(((ToolResponseMessage) second.get(2)).getResponses())
        .singleElement()
        .satisfies(response -> assertThat(response.responseData()).contains("found-paprika"));
    // The tools stay on the options of every round.
    assertThat(prompts.getAllValues().get(1).getOptions()).isNotNull();
  }

  @Test
  void everyRoundIsMeteredAndTheReplyAddsThemUp() {
    when(chat.call(any(Prompt.class)))
        .thenReturn(toolRequest("lookup", new DefaultUsage(100, 10, 110)))
        .thenReturn(answer(ANSWER, new DefaultUsage(150, 20, 170)));

    ModelReply reply = gateway.complete(1L, promptWith(new Lookup()));

    verify(ledger).record(1L, 100, 10);
    verify(ledger).record(1L, 150, 20);
    verify(ledger).assertWithinCap(1L);
    assertThat(reply).isEqualTo(new ModelReply(ANSWER, 250, 30, 14L));
  }

  @Test
  void aModelThatNeverStopsAskingForToolsIsStoppedAtTheRoundLimit() {
    var tool = new Lookup();
    when(chat.call(any(Prompt.class)))
        .thenReturn(toolRequest("lookup", new DefaultUsage(10, 1, 11)));

    assertThatThrownBy(() -> gateway.complete(1L, promptWith(tool)))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining(String.valueOf(ModelGateway.MAX_ROUNDS))
        .message()
        .doesNotContain(PROMPT);

    verify(chat, org.mockito.Mockito.times(ModelGateway.MAX_ROUNDS)).call(any(Prompt.class));
    verify(ledger, org.mockito.Mockito.times(ModelGateway.MAX_ROUNDS)).record(1L, 10, 1);
    assertThat(tool.runs).hasValue(ModelGateway.MAX_ROUNDS);
  }

  @Test
  void aRequestForAToolThePromptDoesNotCarryNamesOnlyTheKindOfFailure() {
    when(chat.call(any(Prompt.class)))
        .thenReturn(toolRequest("noSuchTool", new DefaultUsage(10, 1, 11)));

    assertThatThrownBy(() -> gateway.complete(1L, promptWith(new Lookup())))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("the tool call failed (")
        .hasMessageNotContaining("paprika")
        .hasNoCause();

    verify(chat).call(any(Prompt.class));
  }

  @Test
  void aHouseholdOverItsCapIsRefusedAndTheModelIsNeverCalled() {
    doThrow(new SpendCapExceededException(5_000_000L, 5_000_000L))
        .when(ledger)
        .assertWithinCap(42L);

    assertThatThrownBy(() -> gateway.complete(42L, new Prompt(PROMPT)))
        .isInstanceOf(SpendCapExceededException.class);
    assertThatThrownBy(() -> gateway.embed(42L, "text"))
        .isInstanceOf(SpendCapExceededException.class);

    verifyNoInteractions(chat, embeddings);
    verify(ledger, never()).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void aPromptWithAnEmptyMessageIsRefusedBeforeTheCallAndTheReasonNamesItsKindAndPosition() {
    var prompt =
        new Prompt(
            List.of(
                new SystemMessage(PROMPT),
                new UserMessage(ANSWER),
                new AssistantMessage("  \n"),
                new UserMessage("again")));

    assertThatThrownBy(() -> gateway.complete(1L, prompt))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("empty ASSISTANT message at 2")
        .message()
        .doesNotContain(PROMPT)
        .doesNotContain(ANSWER)
        .doesNotContain("again");

    verifyNoInteractions(chat);
    verify(ledger, never()).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void aMessageWithNoTextAtAllIsRefusedToo() {
    var prompt = new Prompt(List.of(new UserMessage(PROMPT), new AssistantMessage(null)));

    assertThatThrownBy(() -> gateway.complete(1L, prompt))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("empty ASSISTANT message at 1");

    verifyNoInteractions(chat);
  }

  /** A clock the fake model moves: each call "takes" the given time, without sleeping. */
  private static final class FakeTicker implements java.util.function.LongSupplier {
    private long nanos;

    void advance(Duration by) {
      nanos += by.toNanos();
    }

    @Override
    public long getAsLong() {
      return nanos;
    }
  }

  private ModelGateway gatewayWithBudget(Duration requestTimeout, FakeTicker ticker) {
    gateway.close();
    gateway =
        new ModelGateway(
            chat,
            embeddings,
            ledger,
            properties(Duration.ofSeconds(5), requestTimeout),
            ToolCallingManager.builder().build(),
            ticker);
    return gateway;
  }

  @Test
  void aRequestWhoseRoundsUseUpTheTotalBudgetIsStoppedAndKeepsTheRoundsAlreadyRecorded() {
    var ticker = new FakeTicker();
    var tool = new Lookup();
    gatewayWithBudget(Duration.ofSeconds(10), ticker);
    when(chat.call(any(Prompt.class)))
        .thenAnswer(invocation -> {
          ticker.advance(Duration.ofSeconds(4));
          return toolRequest("lookup", new DefaultUsage(10, 1, 11));
        });

    assertThatThrownBy(() -> gateway.complete(1L, promptWith(tool)))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("took longer than PT10S")
        .message()
        .doesNotContain(PROMPT);

    // 4 s, 8 s, 12 s: the third round ends over budget, so no fourth call starts.
    verify(chat, org.mockito.Mockito.times(3)).call(any(Prompt.class));
    verify(ledger, org.mockito.Mockito.times(3)).record(1L, 10, 1);
  }

  @Test
  void aRequestWithinTheTotalBudgetStillReturnsTheReply() {
    var ticker = new FakeTicker();
    gatewayWithBudget(Duration.ofSeconds(10), ticker);
    when(chat.call(any(Prompt.class)))
        .thenAnswer(invocation -> {
          ticker.advance(Duration.ofSeconds(4));
          return toolRequest("lookup", new DefaultUsage(10, 1, 11));
        })
        .thenAnswer(invocation -> {
          ticker.advance(Duration.ofSeconds(4));
          return answer(ANSWER, new DefaultUsage(20, 2, 22));
        });

    ModelReply reply = gateway.complete(1L, promptWith(new Lookup()));

    assertThat(reply.text()).isEqualTo(ANSWER);
    verify(ledger).record(1L, 10, 1);
    verify(ledger).record(1L, 20, 2);
  }

  @Test
  void aCallIsNeverAllowedToRunLongerThanTheBudgetThatIsLeft() {
    var ticker = new FakeTicker();
    gatewayWithBudget(Duration.ofSeconds(10), ticker);
    when(chat.call(any(Prompt.class)))
        .thenAnswer(invocation -> {
          // The first round "takes" 9.9 s of the 10 s, leaving 100 ms; the per-call timeout is 5 s.
          ticker.advance(Duration.ofMillis(9_900));
          return toolRequest("lookup", new DefaultUsage(10, 1, 11));
        })
        .thenAnswer(invocation -> {
          Thread.sleep(4_000);
          return answer(ANSWER, new DefaultUsage(1, 1, 2));
        });

    long started = System.nanoTime();
    assertThatThrownBy(() -> gateway.complete(1L, promptWith(new Lookup())))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("took longer than PT10S");
    long tookMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

    assertThat(tookMillis).isLessThan(2_000);
    verify(ledger).record(1L, 10, 1);
    verify(ledger, org.mockito.Mockito.times(1)).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void aCallWithMoreBudgetThanItsOwnTimeoutKeepsTheOwnTimeout() {
    gateway.close();
    gateway =
        new ModelGateway(
            chat,
            embeddings,
            ledger,
            properties(Duration.ofMillis(50), Duration.ofSeconds(120)),
            ToolCallingManager.builder().build(),
            new FakeTicker());
    when(chat.call(any(Prompt.class)))
        .thenAnswer(invocation -> {
          Thread.sleep(4_000);
          return answer(ANSWER, new DefaultUsage(1, 1, 2));
        });

    assertThatThrownBy(() -> gateway.complete(1L, new Prompt(PROMPT)))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("the call timed out after PT0.05S");
  }

  @Test
  void aSlowCallTimesOutAndNothingIsRecorded() {
    gateway.close();
    gateway =
        new ModelGateway(
            chat,
            embeddings,
            ledger,
            properties(Duration.ofMillis(50)),
            ToolCallingManager.builder().build());
    when(chat.call(any(Prompt.class)))
        .thenAnswer(invocation -> {
          Thread.sleep(5_000);
          return answer(ANSWER, new DefaultUsage(1, 1, 2));
        });

    assertThatThrownBy(() -> gateway.complete(1L, new Prompt(PROMPT)))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("timed out");

    verify(ledger, never()).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void aFailingCallNamesOnlyTheKindOfFailureNeverTheProviderText() {
    when(chat.call(any(Prompt.class)))
        .thenThrow(new IllegalArgumentException("bad request, the prompt was: " + PROMPT));

    assertThatThrownBy(() -> gateway.complete(1L, new Prompt(PROMPT)))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("IllegalArgumentException")
        .hasMessageNotContaining("Szabo")
        .hasNoCause();

    verify(ledger, never()).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void anInterruptedCallKeepsTheInterruptAndRecordsNothing() {
    // The model is slow, so the gateway is still waiting when the interrupt is seen.
    when(chat.call(any(Prompt.class)))
        .thenAnswer(invocation -> {
          Thread.sleep(5_000);
          return answer(ANSWER, new DefaultUsage(1, 1, 2));
        });
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> gateway.complete(1L, new Prompt(PROMPT)))
          .isInstanceOf(ModelUnavailableException.class)
          .hasMessageContaining("interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    verify(ledger, never()).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void nothingTheGatewayDoesLogsThePromptOrTheAnswer() {
    when(chat.call(any(Prompt.class))).thenReturn(answer(ANSWER, new DefaultUsage(100, 50, 150)));
    when(embeddings.embedForResponse(any()))
        .thenReturn(vectors(1, DIMENSIONS, new DefaultUsage(0, 0, 9)));
    when(chat.call(any(Prompt.class))).thenReturn(answer(ANSWER, new DefaultUsage(100, 50, 150)));

    gateway.complete(1L, new Prompt(PROMPT));
    gateway.embed(1L, PROMPT);
    when(chat.call(any(Prompt.class))).thenThrow(new IllegalStateException(PROMPT));
    assertThatThrownBy(() -> gateway.complete(1L, new Prompt(PROMPT)))
        .isInstanceOf(ModelUnavailableException.class);

    assertThat(logs.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(m -> m.contains("Szabo") || m.contains("paprikas") || m.contains("nokedli"));
  }

  @Test
  void embedReturnsOneVectorAndRecordsTheReportedTokensAsInput() {
    when(embeddings.embedForResponse(List.of("lecso")))
        .thenReturn(vectors(1, DIMENSIONS, new DefaultUsage(0, 0, 9)));

    assertThat(gateway.embed(3L, "lecso")).hasSize(DIMENSIONS);

    verify(ledger).assertWithinCap(3L);
    verify(ledger).record(3L, 9, 0);
  }

  @Test
  void embedAllEstimatesTokensWhenNoneAreReported() {
    when(embeddings.embedForResponse(List.of("abcdefgh", "abc")))
        .thenReturn(vectors(2, DIMENSIONS, new DefaultUsage(0, 0, 0)));

    assertThat(gateway.embedAll(3L, List.of("abcdefgh", "abc"))).hasSize(2);

    // 8 chars = 2 tokens, 3 chars = 1 token
    verify(ledger).record(3L, 3, 0);
  }

  @Test
  void embedAllOfNothingMakesNoCall() {
    assertThat(gateway.embedAll(3L, List.of())).isEmpty();

    verifyNoInteractions(embeddings, ledger);
  }

  @Test
  void aVectorOfTheWrongSizeIsRefusedAndNotRecorded() {
    when(embeddings.embedForResponse(any()))
        .thenReturn(vectors(1, DIMENSIONS + 1, new DefaultUsage(0, 0, 9)));

    assertThatThrownBy(() -> gateway.embed(3L, "lecso"))
        .isInstanceOf(ModelUnavailableException.class)
        .hasMessageContaining("expected size of " + DIMENSIONS);

    verify(ledger, never()).record(anyLong(), anyLong(), anyLong());
  }

  @Test
  void aWrongNumberOfVectorsIsRefused() {
    when(embeddings.embedForResponse(any()))
        .thenReturn(vectors(1, DIMENSIONS, new DefaultUsage(0, 0, 9)));

    assertThatThrownBy(() -> gateway.embedAll(3L, List.of("a", "b")))
        .isInstanceOf(ModelUnavailableException.class);
  }
}
