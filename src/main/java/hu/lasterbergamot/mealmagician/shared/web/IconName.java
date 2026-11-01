package hu.lasterbergamot.mealmagician.shared.web;

import java.util.Locale;

/**
 * The names of the icon set (plan-ui-redesign.md Appendix B). {@code ui/_icon.jte} draws the SVG
 * for each name, so a typo in a template is a compile error and not an empty square on the page.
 *
 * <p>To add an icon: add the constant here, then an {@code @elseif} branch with its SVG body in
 * {@code ui/_icon.jte}. The style guide at {@code /dev/ui} shows every constant, and {@code
 * DevUiPageTest} fails when a branch is missing.
 */
public enum IconName {
  CALENDAR,
  MEALS,
  GIFT,
  HOME,
  PLUS,
  CHECK,
  LEFT,
  RIGHT,
  DOWN,
  BREAKFAST,
  LUNCH,
  DINNER,
  SNACK,
  SEARCH,
  X,
  ALERT,
  INFO,
  UP,
  STAR,
  USER,
  MORE,
  COPY,
  SIDEBAR,
  LOGOUT,
  CLOCK,
  EDIT,
  TRASH,
  SPARKLE,
  WAND,
  SPARKLES;

  /**
   * The name used in templates and in the {@code data-icon} attribute: the constant in lower case,
   * for example {@code calendar} for {@link #CALENDAR}.
   *
   * @return the lower-case icon name
   */
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}
