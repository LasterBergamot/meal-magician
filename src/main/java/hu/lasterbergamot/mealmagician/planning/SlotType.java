package hu.lasterbergamot.mealmagician.planning;

/**
 * The part of the day a meal is planned for. The declaration order is the order the week view shows
 * them in, so {@link #ordinal()} is the sort key; the database stores the name.
 */
public enum SlotType {
  /** The morning meal. */
  BREAKFAST,
  /** The midday meal. */
  LUNCH,
  /** The evening meal. */
  DINNER,
  /** Anything in between. */
  SNACK
}
