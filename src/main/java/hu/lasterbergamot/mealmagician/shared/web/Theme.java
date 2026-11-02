package hu.lasterbergamot.mealmagician.shared.web;

import java.util.Locale;

/**
 * The colour theme a person chose: Moonlight ({@link #LIGHT}), Midnight ({@link #DARK}) or {@link
 * #SYSTEM}, which follows the device's {@code prefers-color-scheme} (UI-D8 in {@code
 * plan-ui-redesign.md}).
 *
 * <p>The choice is kept in the first-party cookie {@link #COOKIE_NAME}. The browser writes it (the
 * Alpine {@code theme} component in {@code app.js}), and the server only reads it, so the page's
 * {@code <html data-theme>} is right on the very first paint and nothing flashes. Because the
 * server never writes the cookie there is no state-changing request to protect from CSRF.
 */
public enum Theme {
  /** Moonlight, the light theme, whatever the device prefers. */
  LIGHT,
  /** Midnight, the dark theme, whatever the device prefers. */
  DARK,
  /** No choice made: the device's {@code prefers-color-scheme} picks one. The default. */
  SYSTEM;

  /**
   * Name of the cookie that stores the chosen theme ({@code light}, {@code dark} or {@code
   * system}).
   */
  public static final String COOKIE_NAME = "mm_theme";

  /**
   * The value used in the cookie, in {@code <html data-theme>} and in the CSS selectors of {@code
   * app.css}.
   *
   * @return {@code light}, {@code dark} or {@code system}
   */
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Reads a cookie value leniently: the cookie is under the browser's control, so a missing, empty
   * or unknown value must never break a page.
   *
   * @param value the raw cookie value, or {@code null} when the cookie is absent
   * @return the matching theme, or {@link #SYSTEM} for {@code null} and anything not recognised
   */
  public static Theme fromCookie(String value) {
    for (Theme theme : values()) {
      if (theme.value().equals(value)) {
        return theme;
      }
    }
    return SYSTEM;
  }

  /**
   * The {@code media} attribute for one of the two {@code theme-color} metas in {@code base.jte}.
   * With {@link #SYSTEM} each meta applies to its own {@code prefers-color-scheme}; with an
   * explicit choice only the meta of the chosen scheme applies, so the browser's own bars match the
   * page even when the device prefers the other scheme.
   *
   * @param scheme the scheme the meta is for: {@link #LIGHT} or {@link #DARK}
   * @return a media query: the scheme's {@code prefers-color-scheme} query, {@code all} or {@code
   *     not all}
   */
  public String themeColorMedia(Theme scheme) {
    if (this == SYSTEM) {
      return "(prefers-color-scheme: " + scheme.value() + ")";
    }
    return this == scheme ? "all" : "not all";
  }
}
