package hu.lasterbergamot.mealmagician.planning;

/**
 * What happens to the meals already in the target when a day or week is copied, or a template is
 * applied. The same two modes mean the same thing everywhere, because copy and apply share one
 * writer.
 */
public enum CopyMode {
  /** Adds the copied meals after the meals already in the same date and slot type. */
  MERGE,
  /**
   * First empties the whole target day (or Monday to Sunday for a week), including slot types the
   * source has nothing in, then writes the copy: the target becomes a copy of the source.
   */
  REPLACE
}
