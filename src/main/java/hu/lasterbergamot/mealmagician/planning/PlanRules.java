package hu.lasterbergamot.mealmagician.planning;

/**
 * The input rules for planning, in one place so the services and their tests agree: the slot note,
 * and the names and notes of templates and occasions. Each method returns the cleaned value or
 * throws {@link InvalidPlanException}; none of them touches the database.
 */
final class PlanRules {

  /** The longest note on a slot. */
  static final int NOTE_MAX = 500;

  /** The longest name of a plan template, an occasion or an occasion template. */
  static final int NAME_MAX = 100;

  /** The longest notes text of an occasion or an occasion template. */
  static final int NOTES_MAX = 2000;

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

  /**
   * Cleans the name of a template or an occasion. A name is required, unlike a note.
   *
   * @param raw the typed name, may be {@code null}
   * @return the trimmed name, 1 to {@link #NAME_MAX} characters
   * @throws InvalidPlanException with {@code NAME_REQUIRED} if it is {@code null} or blank, or
   *     {@code NAME_TOO_LONG} if it exceeds {@link #NAME_MAX}
   */
  static String name(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidPlanException(PlanProblem.NAME_REQUIRED);
    }
    String cleaned = raw.strip();
    if (cleaned.length() > NAME_MAX) {
      throw new InvalidPlanException(PlanProblem.NAME_TOO_LONG);
    }
    return cleaned;
  }

  /**
   * Cleans the notes of an occasion or an occasion template. Blank notes are stored as "no notes"
   * ({@code null}).
   *
   * @param raw the typed notes, may be {@code null}
   * @return the trimmed notes, or {@code null} for none
   * @throws InvalidPlanException with {@code NOTES_TOO_LONG} if they exceed {@link #NOTES_MAX}
   */
  static String notes(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String cleaned = raw.strip();
    if (cleaned.length() > NOTES_MAX) {
      throw new InvalidPlanException(PlanProblem.NOTES_TOO_LONG);
    }
    return cleaned;
  }
}
