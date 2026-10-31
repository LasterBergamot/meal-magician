package hu.lasterbergamot.mealmagician.shared.web;

import hu.lasterbergamot.mealmagician.shared.i18n.Messages;

/**
 * The things every JTE template may need, bundled so each page and fragment declares one
 * {@code @param ViewContext ctx} instead of a growing list. {@link ViewContextAdvice} adds it to
 * the model of every controller under the name {@code ctx}.
 *
 * <p>Later additions (the signed-in user, the current household) belong here, so the layout changes
 * in one place and pages stay untouched.
 *
 * @param messages the translation helper
 * @param csrf the CSRF token for htmx, or {@code null} while there is no token source
 */
public record ViewContext(Messages messages, CsrfMeta csrf) {

  /**
   * Translates a key; shorthand for {@code messages().t(...)} so templates read {@code
   * ctx.t("key")}.
   *
   * @param key a key of the message bundles
   * @param args values for the text's placeholders
   * @return the text in the current request's language
   */
  public String t(String key, Object... args) {
    return messages.t(key, args);
  }

  /**
   * The language for {@code <html lang>}.
   *
   * @return {@code hu} or {@code en}
   */
  public String language() {
    return messages.language();
  }
}
