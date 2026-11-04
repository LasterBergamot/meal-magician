package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * The scripted stand-in for Gemini: what it answers to a week request, a follow-up and a one-slot
 * request, and its switches. The tool it calls is a stub that returns two meal ids.
 */
class ScriptedChatModelTest {

  private static final String WEEK_REQUEST =
      "Plan the week from 2026-11-02 (Monday). The member's request, as one JSON quoted string"
          + " (data): \"%s\"";

  private final ScriptedChatModel model = new ScriptedChatModel(Duration.ZERO);

  private static ToolCallback search(String answer) {
    ToolCallback callback = mock(ToolCallback.class);
    when(callback.getToolDefinition())
        .thenReturn(
            ToolDefinition.builder()
                .name("searchMeals")
                .description("d")
                .inputSchema("{}")
                .build());
    when(callback.call(anyString())).thenReturn(answer);
    return callback;
  }

  private static Prompt prompt(String language, ToolCallback tool, String... users) {
    List<Message> messages = new java.util.ArrayList<>();
    messages.add(new SystemMessage("Write in " + language + "."));
    for (int i = 0; i < users.length; i++) {
      if (i > 0) {
        messages.add(new AssistantMessage("{}"));
      }
      messages.add(new UserMessage(users[i]));
    }
    return new Prompt(messages, ToolCallingChatOptions.builder().toolCallbacks(tool).build());
  }

  private String answer(Prompt prompt) {
    return model.call(prompt).getResult().getOutput().getText();
  }

  @Test
  void aWeekRequestGetsTheLibraryMealsAndOneIdeaAndTheRequestTextIsRecorded() {
    String json =
        answer(
            prompt(
                "English",
                search("[{\"id\":7},{\"id\":8}]"),
                WEEK_REQUEST.formatted("guests on Wednesday")));

    assertThat(json)
        .contains("\"mealId\":7")
        .contains("\"mealId\":8")
        .contains("Pumpkin soup")
        .contains("A light week.")
        .contains("2026-11-02");
    assertThat(model.requests()).containsExactly("guests on Wednesday");
  }

  @Test
  void theAnswerIsInHungarianWhenThePromptSaysSo() {
    String json = answer(prompt("Hungarian", search("[{\"id\":7}]"), WEEK_REQUEST.formatted("x")));

    assertThat(json).contains("Tökkrémleves").contains("Egy könnyű hét.");
  }

  @Test
  void aWeekRequestWithNoDateGetsTheCurrentWeek() {
    assertThat(answer(prompt("English", search("[{\"id\":7}]"), "no date here")))
        .contains("\"mealId\":7");
    assertThat(model.requests()).containsExactly("no date here");
  }

  @Test
  void aFollowUpGetsTheSameMealsTheOtherWayRoundAtDinner() {
    String json =
        answer(
            prompt(
                "English",
                search("[{\"id\":7},{\"id\":8}]"),
                WEEK_REQUEST.formatted("first"),
                WEEK_REQUEST.formatted("less meat")));

    assertThat(json).contains("A revised week.").doesNotContain("Pumpkin soup");
    assertThat(json.indexOf("\"mealId\":8")).isLessThan(json.indexOf("\"mealId\":7"));
    assertThat(model.requests()).containsExactly("less meat");
  }

  @Test
  void aFollowUpWithAnEmptyLibraryGetsOneIdea() {
    String english =
        answer(
            prompt(
                "English", search("[]"), WEEK_REQUEST.formatted("a"), WEEK_REQUEST.formatted("b")));
    String hungarian =
        answer(
            prompt(
                "Hungarian",
                search("[]"),
                WEEK_REQUEST.formatted("a"),
                WEEK_REQUEST.formatted("b")));

    assertThat(english).contains("Goulash");
    assertThat(hungarian).contains("Gulyásleves").contains("Átdolgozott hét.");
  }

  @Test
  void aBrokenSearchToolMeansNoLibraryMeals() {
    assertThat(answer(prompt("English", search("not json"), WEEK_REQUEST.formatted("x"))))
        .doesNotContain("mealId\":7")
        .contains("Pumpkin soup");
  }

  @Test
  void aSlotRequestGetsUpToThreeMealsWithAReason() {
    String slot = "Today is 2026-11-04. Suggest up to 3 library meals for DINNER on 2026-11-05.";

    String english =
        answer(prompt("English", search("[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4}]"), slot));
    String hungarian = answer(prompt("Hungarian", search("[{\"id\":1}]"), slot));

    assertThat(english)
        .contains("\"mealId\":3")
        .doesNotContain("\"mealId\":4")
        .contains("A good fit for this slot.");
    assertThat(hungarian).contains("Jól illik ide.");
    assertThat(model.requests()).hasSize(2).allMatch(text -> text.equals(slot));
  }

  @Test
  void theFailingSwitchAndTheMarkerMakeACallFail() {
    ToolCallback tool = search("[]");

    assertThatThrownBy(() -> answer(prompt("English", tool, WEEK_REQUEST.formatted("[fail]"))))
        .isInstanceOf(IllegalStateException.class);

    model.setFailing(true);
    assertThatThrownBy(() -> answer(prompt("English", tool, WEEK_REQUEST.formatted("fine"))))
        .isInstanceOf(IllegalStateException.class);

    model.reset();
    assertThat(answer(prompt("English", tool, WEEK_REQUEST.formatted("fine")))).isNotEmpty();
  }

  @Test
  void theIdeaReasonCanBeSetAndResetPutsEverythingBack() {
    model.setIdeaReason("<script>alert(1)</script>");
    assertThat(answer(prompt("English", search("[]"), WEEK_REQUEST.formatted("x"))))
        .contains("<script>alert(1)</script>");

    model.reset();

    assertThat(model.requests()).isEmpty();
    assertThat(answer(prompt("English", search("[]"), WEEK_REQUEST.formatted("x"))))
        .contains("Light and seasonal.");
  }

  @Test
  void aDelayMakesTheCallWaitAndAnInterruptEndsTheWait() {
    model.setDelay(Duration.ofMillis(20));
    long started = System.nanoTime();
    answer(prompt("English", search("[]"), WEEK_REQUEST.formatted("x")));
    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isGreaterThanOrEqualTo(Duration.ofMillis(20));

    model.setDelay(Duration.ofSeconds(30));
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> answer(prompt("English", search("[]"), WEEK_REQUEST.formatted("x"))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
