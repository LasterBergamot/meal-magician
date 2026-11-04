package hu.lasterbergamot.mealmagician.assistant.internal;

/**
 * One turn of a draft's conversation, as it is stored (a JSON array of these in {@code
 * assistant_draft.conversation}). Only what a member typed and what the model answered is kept; the
 * date, the week and the current draft are added again each time the prompt is built, so they never
 * go stale in storage.
 *
 * @param role {@link #USER} or {@link #ASSISTANT}
 * @param text the member's request or follow-up message, or the model's raw JSON answer
 */
record Turn(String role, String text) {

  /** A turn typed by a member. */
  static final String USER = "user";

  /** A turn answered by the model. */
  static final String ASSISTANT = "assistant";

  static Turn user(String text) {
    return new Turn(USER, text);
  }

  static Turn assistant(String text) {
    return new Turn(ASSISTANT, text);
  }

  boolean fromMember() {
    return USER.equals(role);
  }
}
