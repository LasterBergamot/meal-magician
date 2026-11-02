package hu.lasterbergamot.mealmagician.catalog.internal;

/**
 * The model of one quick-add form (name only). The page has two of them, a sheet for phones and an
 * inline bar for larger screens, so the form says which one it is: that keeps their element ids
 * apart and lets a rejected post redraw the same form it came from.
 *
 * @param variant {@link #SHEET} or {@link #BAR}
 * @param name the text typed so far
 * @param error the message key of the problem to show, or {@code null}
 */
public record QuickAdd(String variant, String name, String error) {

  /** The form inside the phone's bottom sheet. */
  public static final String SHEET = "quick-sheet";

  /** The inline bar shown from the tablet width up. */
  public static final String BAR = "quick-bar";

  /**
   * A fresh form without text or error.
   *
   * @param variant {@link #SHEET} or {@link #BAR}
   * @return the model
   */
  public static QuickAdd empty(String variant) {
    return new QuickAdd(variant, "", null);
  }

  /**
   * Reads the variant a form posted back. Anything unknown means the bar, so a hand-edited value
   * cannot put arbitrary text into an element id.
   *
   * @param posted the hidden field's value, may be {@code null}
   * @return {@link #SHEET} or {@link #BAR}
   */
  public static String variantOf(String posted) {
    return SHEET.equals(posted) ? SHEET : BAR;
  }

  /**
   * Whether this is the compact bar layout.
   *
   * @return {@code true} for the bar, {@code false} for the sheet
   */
  public boolean inline() {
    return BAR.equals(variant);
  }
}
