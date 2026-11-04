package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.ChatTurn;
import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.InvalidRequestException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Answers one chat message (#161): the topic policy, the stored conversation, the new message and
 * the read-only tools of the week drafts, all sent through the one gateway. {@code
 * AssistantService} is the public face and checks that the assistant is on; this class does the
 * work.
 *
 * <p>The answer is plain text, not JSON, so nothing is validated beyond being non-empty. The text
 * the model writes is untrusted: the page escapes it and the controller never stores it anywhere
 * but the session. No database transaction is open during the model call.
 */
@Service
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class ChatService {

  private final ModelGateway gateway;
  private final PromptBuilder prompts;
  private final DraftGenerator tools;
  private final HouseholdService households;
  private final Clock clock;
  private final String policy;

  /**
   * Creates the service and reads the topic policy once.
   *
   * @param gateway the only door to the model
   * @param prompts builds the messages
   * @param tools the draft generator, which builds the read-only tools for one household
   * @param households does the membership check
   * @param properties the {@code mm.ai.*} settings, for the chat scope
   * @param clock the application clock
   * @throws IOException if the policy file of the chosen scope is missing from the build, so the
   *     application fails to start rather than answer without a policy
   */
  public ChatService(
      ModelGateway gateway,
      PromptBuilder prompts,
      DraftGenerator tools,
      HouseholdService households,
      AiProperties properties,
      Clock clock)
      throws IOException {
    this.gateway = gateway;
    this.prompts = prompts;
    this.tools = tools;
    this.households = households;
    this.clock = clock;
    this.policy = ChatScope.of(properties.chatScope()).policy();
  }

  /**
   * Answers one message of a conversation.
   *
   * @param userId the {@code app_user} id of the member asking; the tools run as them
   * @param householdId the household
   * @param locale the member's language, which the answer is written in
   * @param history the conversation so far, oldest first; empty for a new one
   * @param message the new message, at most 1 000 characters; {@code null} counts as empty
   * @return the answer's plain text, stripped of surrounding white space
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws InvalidRequestException if the message is longer than 1 000 characters; no model call
   *     was made
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household has
   *     used up its allowance; no model call was made
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model failed
   * @throws DraftGenerationException if the model answered with nothing
   */
  public String reply(
      long userId, long householdId, Locale locale, List<ChatTurn> history, String message) {
    households.require(householdId, userId);
    String text = Objects.requireNonNullElse(message, "");
    if (text.length() > InvalidRequestException.CHAT_MAX_LENGTH) {
      throw new InvalidRequestException(text.length(), InvalidRequestException.CHAT_MAX_LENGTH);
    }
    List<Message> messages = prompts.buildChat(locale, LocalDate.now(clock), policy, history, text);
    String answer =
        gateway
            .complete(householdId, new Prompt(messages, tools.toolOptions(userId, householdId)))
            .text()
            .strip();
    if (answer.isEmpty()) {
      throw new DraftGenerationException("the answer was empty");
    }
    return answer;
  }
}
