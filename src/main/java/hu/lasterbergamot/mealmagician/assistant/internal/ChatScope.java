package hu.lasterbergamot.mealmagician.assistant.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/**
 * The topics the chat wizard may talk about, chosen by {@code mm.ai.chat-scope} (Phase 7, #161).
 * Each scope names one resource file that holds the policy text the model reads, so widening the
 * chat later is a new file and a new constant here, plus a config change; no logic changes.
 *
 * <p>The policy is a prompt, not a security control. The tools behind the chat are read-only and
 * bound to the asking household whatever the text says.
 */
enum ChatScope {

  /** Meals, ingredients, seasons in Hungary, menus and the household's own history only. */
  FOOD_AND_PLAN("food-and-plan", "assistant/chat-policy-food.txt");

  private final String value;
  private final String resource;

  ChatScope(String value, String resource) {
    this.value = value;
    this.resource = resource;
  }

  /**
   * Finds the scope named by a configuration value.
   *
   * @param value the value as written in {@code mm.ai.chat-scope}, for example {@code
   *     food-and-plan}
   * @return the scope
   * @throws IllegalArgumentException if no scope has that name; this makes the application refuse
   *     to start, rather than run with a topic policy nobody chose
   */
  static ChatScope of(String value) {
    for (ChatScope scope : values()) {
      if (scope.value.equals(value)) {
        return scope;
      }
    }
    throw new IllegalArgumentException(
        "mm.ai.chat-scope must be one of: food-and-plan (it was \"" + value + "\")");
  }

  /**
   * Reads the policy text of this scope from the classpath, UTF-8.
   *
   * @return the text, to be put into the system prompt as it is
   * @throws IOException if the resource is missing from the build; the caller is the constructor of
   *     the chat service, so the application fails to start
   */
  String policy() throws IOException {
    return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
  }
}
