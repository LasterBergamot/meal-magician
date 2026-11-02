package hu.lasterbergamot.mealmagician.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * Remembers the language a person picked in the database, so that it is used again after their next
 * sign-in (the stored per-user language, PLAN.md D14, {@code app_user.locale}).
 *
 * <p>The cookie that {@link LanguageController} sets is per browser; this is the part that follows
 * the person to another device. {@code shared} only declares the interface, the {@code identity}
 * module implements it, so {@code shared} does not depend on {@code identity}.
 */
@FunctionalInterface
public interface LanguageStore {

  /**
   * Saves the language for the person who made the request, if there is one.
   *
   * <p>An anonymous request has nobody to save for and does nothing; the cookie still applies to
   * it.
   *
   * @param request the request that asked for the switch
   * @param locale the supported language the person picked
   */
  void remember(HttpServletRequest request, Locale locale);
}
