package hu.lasterbergamot.mealmagician.planning;

/**
 * The input rules for a planned slot, in one place so the service and its tests agree. The only
 * free text is the slot note. Each method returns the cleaned value or throws {@link
 * InvalidPlanException}; none of them touches the database.
 */
final class PlanRules {

  /** The longest note on a slot. */
  static final int NOTE_MAX = 500;

  private PlanRules() {}

  /**
   * Cleans a slot note. Blank notes are stored as "no note" ({@code null}), so the database does
   * not distinguish "" from "never filled in".
   *
   * @param raw the typed note, may be {@code null}
   * @return the trimmed note, or {@code null} for none
   * @throws InvalidPlanException with {@code NOTE_TOO_LONG} if it exceeds {@link #NOTE_MAX}
   */
  static String note(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String cleaned = raw.strip();
    if (cleaned.length() > NOTE_MAX) {
      throw new InvalidPlanException(PlanProblem.NOTE_TOO_LONG);
    }
    return cleaned;
  }
}
