package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.assistant.ChatTurn;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

class ChatPromptTest {

  private final PromptBuilder prompts = new PromptBuilder();

  @Test
  void theChatPromptIsInEnglishForAnEnglishMemberAndCarriesThePolicy() {
    String system = prompts.chatSystemPrompt(Locale.ENGLISH, "POLICY TEXT");

    assertThat(system)
        .contains("in English")
        .contains("POLICY TEXT")
        .contains("the chat assistant");
  }

  @Test
  void theChatMessagesKeepTheHistoryInOrderAndQuoteEveryMemberText() {
    List<Message> messages =
        prompts.buildChat(
            Locale.ENGLISH,
            LocalDate.of(2026, 11, 4),
            "POLICY",
            List.of(ChatTurn.user("first \"quoted\""), ChatTurn.assistant("answer")),
            "second");

    assertThat(messages).hasSize(4);
    assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
    assertThat(messages.get(1).getText())
        .contains("\"first \\\"quoted\\\"\"")
        .contains("quoted string (data)");
    assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
    assertThat(messages.get(2).getText()).isEqualTo("answer");
    assertThat(messages.get(3).getText()).startsWith("Today is 2026-11-04.").contains("\"second\"");
  }

  @Test
  void theMenuPromptIsInHungarianForAHungarianMemberAndKeepsTheOccasionAsData() {
    String system = prompts.menuSystemPrompt(Locale.of("hu"), "FORMAT");

    assertThat(system)
        .contains("in Hungarian")
        .contains("suggest a menu for ONE occasion")
        .contains("FORMAT");
  }

  @Test
  void theMenuMessageCarriesTheOccasionAndItsEarlierMenusAsJson() {
    List<Message> messages =
        prompts.buildMenu(
            Locale.ENGLISH,
            LocalDate.of(2026, 11, 4),
            Map.of("name", "Anna"),
            List.of(Map.of("name", "Last year", "meals", List.of())),
            "FORMAT");

    assertThat(messages).hasSize(2);
    assertThat(messages.get(1).getText())
        .contains("{\"name\":\"Anna\"}")
        .contains("\"name\":\"Last year\"")
        .contains("Suggest a menu for this occasion (data)");
  }
}
