package hu.lasterbergamot.mealmagician.shared.web;

import hu.lasterbergamot.mealmagician.shared.i18n.Messages;

/**
 * The things every JTE template may need, bundled so each page and fragment declares one
 * {@code @param ViewContext ctx} instead of a growing list. {@link ViewContextAdvice} adds it to
 * the model of every controller under the name {@code ctx}.
 *
 * <p>Later additions belong here, so the layout changes in one place and pages stay untouched.
 *
 * @param messages the translation helper
 * @param csrf the CSRF token for htmx and the sign-out and switch forms, or {@code null} while
 *     there is no token source
 * @param household the household for the shell, or {@code null} when there is none to show
 * @param admin whether the person is an instance admin, which adds the allowlist link
 * @param user the signed-in person for the app shell, or {@code null} for an anonymous request,
 *     which gets the plain layout without the shell
 * @param active the navigation destination the request belongs to (its tab is highlighted), or
 *     {@code null} when no tab does
 * @param theme the colour theme from the {@code mm_theme} cookie, {@link Theme#SYSTEM} when there
 *     is none or it is not recognised
 */
public record ViewContext(
    Messages messages,
    CsrfMeta csrf,
    HouseholdNav household,
    boolean admin,
    ShellUser user,
    NavDestination active,
    Theme theme) {

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
