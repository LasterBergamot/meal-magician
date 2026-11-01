package hu.lasterbergamot.mealmagician.catalog;

/**
 * Why a meal or tag was refused. The enum carries no message text on purpose: the page that shows
 * the refusal chooses the translated message for each value, next to the form it belongs to.
 */
public enum MealProblem {
  /** The name is empty or only blanks. */
  NAME_REQUIRED,
  /** The name is longer than 200 characters. */
  NAME_TOO_LONG,
  /** The notes are longer than 2000 characters. */
  NOTES_TOO_LONG,
  /**
   * The recipe link is not an {@code http} or {@code https} address, or is longer than 2000
   * characters.
   */
  RECIPE_URL_INVALID,
  /** A tag name is empty or longer than 50 characters. */
  TAG_INVALID
}
