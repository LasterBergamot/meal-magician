package hu.lasterbergamot.mealmagician.shared.i18n;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Looks up UI text for the language of the current request. This is the {@code t("key")} helper
 * (PLAN.md D14); templates reach it through {@code ViewContext.t(...)}.
 *
 * <p>It is a plain object that templates receive as a typed parameter instead of a static method on
 * global state: that keeps templates compile-checked and tests free of hidden state. The language
 * is read from the request's locale ({@link LocaleContextHolder}), which the cookie-based locale
 * resolver fills in, so callers never pass a locale around.
 */
public class Messages {

  private final MessageSource messageSource;

  /**
   * Creates the helper.
   *
   * @param messageSource the source of {@code messages_hu} and {@code messages_en}
   */
  public Messages(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * Translates a key into the current request's language.
   *
   * @param key a key that exists in both message bundles
   * @param args values for the {@code {0}}, {@code {1}}, ... placeholders of the text
   * @return the translated text
   * @throws org.springframework.context.NoSuchMessageException if the key is missing; this is
   *     deliberate, a test that checks every template key makes sure it never happens in production
   */
  public String t(String key, Object... args) {
    return messageSource.getMessage(key, args, locale());
  }

  /**
   * The supported locale of the current request.
   *
   * @return {@link SupportedLocales#HUNGARIAN} or {@link SupportedLocales#ENGLISH}
   */
  public Locale locale() {
    return SupportedLocales.normalize(LocaleContextHolder.getLocale());
  }

  /**
   * The language code for the {@code <html lang>} attribute.
   *
   * @return {@code hu} or {@code en}
   */
  public String language() {
    return locale().getLanguage();
  }
}
