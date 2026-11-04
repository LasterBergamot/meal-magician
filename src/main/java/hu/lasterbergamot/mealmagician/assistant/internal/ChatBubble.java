package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.ChatTurn;
import java.util.List;

/**
 * One message as the chat page draws it: a member's message (right, on the brand colour) or the
 * wizard's answer (left, outlined). The text is plain; the template escapes it and keeps its line
 * breaks, so no model text is ever rendered as HTML or markdown.
 *
 * @param member {@code true} for a message a member typed
 * @param text the message's text
 */
public record ChatBubble(boolean member, String text) {

  /**
   * Turns stored turns into bubbles, in the same order.
   *
   * @param turns the turns, oldest first
   * @return one bubble per turn
   */
  static List<ChatBubble> of(List<ChatTurn> turns) {
    return turns.stream().map(turn -> new ChatBubble(turn.fromMember(), turn.text())).toList();
  }
}
