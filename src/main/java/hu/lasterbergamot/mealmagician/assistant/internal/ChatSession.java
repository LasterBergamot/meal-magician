package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.ChatTurn;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

/**
 * The chat's history in the signed-in session (#161). The chat is kept under one session attribute
 * and never written to a table, so it lives as long as the session does: "New conversation" clears
 * it, and a sign-out or an expired session drops it with the rest of the session.
 *
 * <p>The list is bounded: only the last {@link #MAX_TURNS} messages are kept, so the prompt and the
 * session both stay small. A new list is stored on every change, because the session store only
 * saves an attribute when it is set again.
 */
final class ChatSession {

  /** The session attribute that holds the chat turns. */
  static final String ATTRIBUTE = "mm.assistant.chat";

  /** How many turns (member messages and answers together) a conversation keeps. */
  static final int MAX_TURNS = 20;

  private ChatSession() {}

  /**
   * The turns stored so far, oldest first. A value of another type under the attribute (which only
   * a bug or a tampered store could produce) is treated as no history rather than as an error.
   *
   * @param session the signed-in session
   * @return an unmodifiable copy; empty for a new conversation
   */
  static List<ChatTurn> read(HttpSession session) {
    Object stored = session.getAttribute(ATTRIBUTE);
    if (stored instanceof List<?> turns) {
      return turns.stream().filter(ChatTurn.class::isInstance).map(ChatTurn.class::cast).toList();
    }
    return List.of();
  }

  /**
   * Adds a question and its answer to the end of the conversation, dropping the oldest turns when
   * the conversation is longer than {@link #MAX_TURNS}.
   *
   * @param session the signed-in session
   * @param question the member's message
   * @param answer the wizard's answer
   */
  static void append(HttpSession session, ChatTurn question, ChatTurn answer) {
    List<ChatTurn> turns = new ArrayList<>(read(session));
    turns.add(question);
    turns.add(answer);
    while (turns.size() > MAX_TURNS) {
      turns.removeFirst();
    }
    session.setAttribute(ATTRIBUTE, turns);
  }

  /**
   * Forgets the conversation ("New conversation").
   *
   * @param session the signed-in session
   */
  static void clear(HttpSession session) {
    session.removeAttribute(ATTRIBUTE);
  }
}
