package hu.lasterbergamot.mealmagician.shared.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The languages the UI is translated into: Hungarian (the default) and English.
 *
 * <p>Anything else a browser or a tampered cookie might ask for is mapped to Hungarian, so a
 * message lookup can never fail because of an unknown locale (there is no base {@code
 * messages.properties} to fall back to).
 */
public final class SupportedLocales {

  /** The default language of the household (PLAN.md D14). */
  public static final Locale HUNGARIAN = Locale.of("hu");

  /** The only other language. */
  public static final Locale ENGLISH = Locale.of("en");

  /** Every language the language switcher offers, default first. */
  public static final List<Locale> ALL = List.of(HUNGARIAN, ENGLISH);

  private SupportedLocales() {}

  /**
   * Maps any locale to a supported one.
   *
   * @param locale any locale, for example from the request
   * @return {@link #ENGLISH} for English in any variant, otherwise {@link #HUNGARIAN}
   */
  public static Locale normalize(Locale locale) {
    return ENGLISH.getLanguage().equals(locale.getLanguage()) ? ENGLISH : HUNGARIAN;
  }

  /**
   * Parses a language code from a request parameter.
   *
   * @param code a code such as {@code hu} or {@code en}; may be {@code null}
   * @return the matching supported locale, or empty if the code is {@code null} or unsupported
   */
  public static Optional<Locale> parse(String code) {
    return ALL.stream().filter(locale -> locale.getLanguage().equals(code)).findFirst();
  }
}
