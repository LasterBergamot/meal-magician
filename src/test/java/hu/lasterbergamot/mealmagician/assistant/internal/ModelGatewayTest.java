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
    return new AiProperties(
        true,
        new BigDecimal("5.00"),
        new BigDecimal("1.65"),
        new BigDecimal("9.90"),
        DIMENSIONS,
        timeout,
        "food-and-plan");
  }

  @BeforeEach
  void gateway() {
    gateway = new ModelGateway(chat, embeddings, ledger, properties(Duration.ofSeconds(5)));
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
  void aSlowCallTimesOutAndNothingIsRecorded() {
    gateway.close();
    gateway = new ModelGateway(chat, embeddings, ledger, properties(Duration.ofMillis(50)));
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
