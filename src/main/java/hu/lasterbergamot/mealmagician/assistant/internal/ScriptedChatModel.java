package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in for Gemini that answers from a script, so the assistant's screens can be built, tested
 * and screenshotted with no Google account, no network and no cost. It exists only in the {@code
 * fake-ai} profile (see {@link FakeAiConfig}); {@code ProductionConfigGuard} refuses that profile
 * in production.
 *
 * <p>It behaves like the real model in the way that matters to the server: it reads the prompt,
 * calls the read-only tool {@code searchMeals} that the prompt carries (so the ids it names are
 * real ids of the asking household) and answers with JSON in the format the prompt asked for. The
 * answers are fixed:
 *
 * <ul>
 *   <li>a week request gets up to five library meals on the first days of the week plus one idea on
 *       Saturday;
 *   <li>a follow-up gets the same library meals in the opposite order, all at dinner, and no idea,
 *       so a refined draft visibly differs from the first one;
 *   <li>a one-slot request gets up to three library meals with a short reason.
 * </ul>
 *
 * <p>Three switches make the other states reachable: {@link #setDelay} (the waiting state and the
 * Cancel path), {@link #setFailing} and a request containing the text {@code [fail]} (the
 * "unavailable" state). Every request text the model was given is kept in {@link #requests()}, so a
 * test can check what the controller sent.
 */
class ScriptedChatModel implements ChatModel {

  /** The text that makes one call fail, whatever the switches say. */
  static final String FAIL_MARKER = "[fail]";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Pattern WEEK_START =
      Pattern.compile("from (\\d{4}-\\d{2}-\\d{2}) \\(Monday\\)");
  private static final Pattern SLOT_REQUEST = Pattern.compile("Suggest up to 3 library meals for");
  private static final String QUOTE_MARKER = "quoted string (data): ";
  private static final int MAX_WEEK_MEALS = 5;
  private static final int MAX_SLOT_MEALS = 3;

  private final List<String> requests = new CopyOnWriteArrayList<>();
  private volatile Duration delay;
  private volatile boolean failing;
  private volatile String ideaReason = "Light and seasonal.";

  /**
   * Creates the model.
   *
   * @param delay how long every call waits before it answers; {@link Duration#ZERO} for none
   */
  ScriptedChatModel(Duration delay) {
    this.delay = delay;
  }

  /**
   * Sets how long every call waits before it answers.
   *
   * @param delay the wait; {@link Duration#ZERO} for none
   */
  void setDelay(Duration delay) {
    this.delay = delay;
  }

  /**
   * Makes every call fail (as an unreachable Google would) until switched off again.
   *
   * @param failing {@code true} to fail every call
   */
  void setFailing(boolean failing) {
    this.failing = failing;
  }

  /**
   * Sets the reason the week answer gives for its idea. A test uses it to put hostile text into a
   * model answer and check that a page escapes it.
   *
   * @param ideaReason the idea's reason
   */
  void setIdeaReason(String ideaReason) {
    this.ideaReason = ideaReason;
  }

  /**
   * The request texts the model has been given, oldest first: what a member typed (with the
   * quick-request phrases the controller appended), or the whole user message of a one-slot call.
   *
   * @return a snapshot of the texts
   */
  List<String> requests() {
    return List.copyOf(requests);
  }

  /** Forgets the recorded request texts and puts every switch back to its normal state. */
  void reset() {
    requests.clear();
    delay = Duration.ZERO;
    failing = false;
    ideaReason = "Light and seasonal.";
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    List<String> userMessages = new ArrayList<>();
    for (Message message : prompt.getInstructions()) {
      if (message.getMessageType() == MessageType.USER) {
        userMessages.add(message.getText());
      }
    }
    String last = userMessages.getLast();
    String requestText = requestTextOf(last);
    requests.add(requestText);
    pause();
    if (failing || requestText.contains(FAIL_MARKER)) {
      throw new IllegalStateException("scripted failure");
    }
    boolean hungarian = prompt.getInstructions().getFirst().getText().contains("in Hungarian");
    List<Long> ids = libraryIds(prompt);
    String answer;
    if (SLOT_REQUEST.matcher(last).find()) {
      answer = slotAnswer(ids, hungarian);
    } else {
      Matcher week = WEEK_START.matcher(userMessages.getFirst());
      LocalDate monday = week.find() ? LocalDate.parse(week.group(1)) : LocalDate.now();
      answer =
          userMessages.size() == 1
              ? firstAnswer(monday, ids, hungarian)
              : refinedAnswer(monday, ids, hungarian);
    }
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(answer))),
        ChatResponseMetadata.builder().usage(new DefaultUsage(10, 10, 20)).build());
  }

  private void pause() {
    Duration wait = delay;
    if (wait.isZero()) {
      return;
    }
    try {
      Thread.sleep(wait);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted", e);
    }
  }

  /** The member's text out of the quoted string the prompt builder put in the user message. */
  private static String requestTextOf(String userMessage) {
    int at = userMessage.lastIndexOf(QUOTE_MARKER);
    if (at < 0) {
      return userMessage;
    }
    return JSON.readValue(userMessage.substring(at + QUOTE_MARKER.length()).strip(), String.class);
  }

  /**
   * The household's meal ids, got the way the real model gets them: from the search tool. A tool
   * that fails (for example because the data is not committed yet in a test that wraps everything
   * in one transaction) gives no ids, and the answer then holds only ideas.
   */
  private static List<Long> libraryIds(Prompt prompt) {
    List<ToolCallback> callbacks =
        ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
    ToolCallback search =
        callbacks.stream()
            .filter(callback -> callback.getToolDefinition().name().equals("searchMeals"))
            .findFirst()
            .orElseThrow();
    List<Long> ids = new ArrayList<>();
    try {
      for (JsonNode meal : JSON.readTree(search.call("{\"query\":\"meals\",\"limit\":15}"))) {
        ids.add(meal.get("id").asLong());
      }
    } catch (RuntimeException toolFailed) {
      return List.of();
    }
    return ids;
  }

  private String firstAnswer(LocalDate monday, List<Long> ids, boolean hungarian) {
    List<DraftProposal.ProposedItem> items = new ArrayList<>();
    for (int i = 0; i < Math.min(ids.size(), MAX_WEEK_MEALS); i++) {
      items.add(
          new DraftProposal.ProposedItem(
              monday.plusDays(i), i % 2 == 0 ? SlotType.LUNCH : SlotType.DINNER, ids.get(i), null));
    }
    items.add(
        new DraftProposal.ProposedItem(
            monday.plusDays(5),
            SlotType.DINNER,
            null,
            new DraftProposal.Idea(
                hungarian ? "Tökkrémleves" : "Pumpkin soup",
                ideaReason,
                List.of("soup"),
                List.of("pumpkin", "cream"))));
    return JSON.writeValueAsString(
        new DraftProposal(hungarian ? "Egy könnyű hét." : "A light week.", items));
  }

  private String refinedAnswer(LocalDate monday, List<Long> ids, boolean hungarian) {
    List<DraftProposal.ProposedItem> items = new ArrayList<>();
    List<Long> reversed = ids.subList(0, Math.min(ids.size(), MAX_WEEK_MEALS)).reversed();
    for (int i = 0; i < reversed.size(); i++) {
      items.add(
          new DraftProposal.ProposedItem(
              monday.plusDays(i), SlotType.DINNER, reversed.get(i), null));
    }
    if (items.isEmpty()) {
      items.add(
          new DraftProposal.ProposedItem(
              monday,
              SlotType.DINNER,
              null,
              new DraftProposal.Idea(
                  hungarian ? "Gulyásleves" : "Goulash", ideaReason, List.of(), List.of())));
    }
    return JSON.writeValueAsString(
        new DraftProposal(hungarian ? "Átdolgozott hét." : "A revised week.", items));
  }

  private static String slotAnswer(List<Long> ids, boolean hungarian) {
    List<SlotProposal.Pick> picks = new ArrayList<>();
    for (Long id : ids.subList(0, Math.min(ids.size(), MAX_SLOT_MEALS))) {
      picks.add(
          new SlotProposal.Pick(id, hungarian ? "Jól illik ide." : "A good fit for this slot."));
    }
    return JSON.writeValueAsString(new SlotProposal(picks));
  }
}
