package hu.lasterbergamot.mealmagician.assistant;

/**
 * Where a draft is in its short life. A draft that is discarded is deleted, so there is no state
 * for it.
 */
public enum DraftStatus {
  /** The draft can still be refined, trimmed and accepted. */
  OPEN,
  /** The draft was written into the plan; it is kept (read-only) until it is purged. */
  ACCEPTED
}
