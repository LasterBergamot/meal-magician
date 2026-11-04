package hu.lasterbergamot.mealmagician.assistant;

import java.io.Serializable;

/**
 * One message of a chat with the wizard, as the signed-in session keeps it: a question a member
 * typed, or an answer the model gave. The chat lives in the session only (Spring Session stores it
 * with the session's other attributes), never in a table, so it is gone when the session expires or
 * the member signs out, and "New conversation" clears it.
 *
 * <p>The record is {@link Serializable} because the session store writes it with Java
 * serialisation, and the session can outlive a restart of the app.
 *
 * @param role {@link #USER} for a message a member typed, {@link #ASSISTANT} for the wizard's
 *     answer
 * @param text the plain text: at most 1 000 characters for a member, the model's answer otherwise.
 *     It is escaped when shown, never rendered as HTML
 */
public record ChatTurn(String role, String text) implements Serializable {

  /** The serialisation version; the record's shape is fixed, so one version is enough. */
  private static final long serialVersionUID = 1L;

  /** The role of a message a member typed. */
  public static final String USER = "user";

  /** The role of an answer the wizard gave. */
  public static final String ASSISTANT = "assistant";

  /**
   * A message a member typed.
   *
   * @param text the message
   * @return the turn, with the {@link #USER} role
   */
  public static ChatTurn user(String text) {
    return new ChatTurn(USER, text);
  }

  /**
   * An answer of the wizard.
   *
   * @param text the answer
   * @return the turn, with the {@link #ASSISTANT} role
   */
  public static ChatTurn assistant(String text) {
    return new ChatTurn(ASSISTANT, text);
  }

  /**
   * Whether a member typed this turn (as opposed to the wizard answering).
   *
   * @return {@code true} for a member's message
   */
  public boolean fromMember() {
    return USER.equals(role);
  }
}
