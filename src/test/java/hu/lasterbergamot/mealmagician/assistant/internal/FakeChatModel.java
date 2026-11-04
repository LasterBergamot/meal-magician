package hu.lasterbergamot.mealmagician.assistant.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * A scripted stand-in for Gemini: each call takes the next answer from the script and remembers the
 * prompt it was given. An answer may be computed from the prompt, and can call the tools the prompt
 * carries ({@link #tool}) the way the real model would, so a test sees exactly what a tool returns
 * to the model. No network, no credentials.
 */
final class FakeChatModel implements ChatModel {

  /** Every prompt received, in order. */
  final List<Prompt> prompts = new ArrayList<>();

  private final Deque<Function<Prompt, String>> script = new ArrayDeque<>();

  /**
   * Queues an answer.
   *
   * @param answer computes the model's text from the prompt
   * @return this
   */
  FakeChatModel thenAnswer(Function<Prompt, String> answer) {
    script.add(answer);
    return this;
  }

  /**
   * Queues a fixed answer.
   *
   * @param text the model's text
   * @return this
   */
  FakeChatModel thenReply(String text) {
    return thenAnswer(prompt -> text);
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    prompts.add(prompt);
    String text = script.removeFirst().apply(prompt);
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(text))),
        ChatResponseMetadata.builder().usage(new DefaultUsage(10, 10, 20)).build());
  }

  /**
   * Calls one of the tools a prompt carries, as the model would.
   *
   * @param prompt the prompt the model received
   * @param name the tool's name
   * @param jsonArguments the arguments as JSON
   * @return the tool's result, as the model would see it (JSON text)
   */
  static String tool(Prompt prompt, String name, String jsonArguments) {
    List<ToolCallback> callbacks =
        ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
    return callbacks.stream()
        .filter(callback -> callback.getToolDefinition().name().equals(name))
        .findFirst()
        .orElseThrow()
        .call(jsonArguments);
  }

  /**
   * The text of every message of a prompt, for "no name leaked" assertions.
   *
   * @param prompt a prompt
   * @return all message texts, joined
   */
  static String text(Prompt prompt) {
    return prompt.getInstructions().stream()
        .map(message -> message.getText())
        .collect(java.util.stream.Collectors.joining("\n"));
  }
}
