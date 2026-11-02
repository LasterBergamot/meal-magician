package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.i18n.SupportedLocales;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Applies the language a person stored in the database when they sign in (PLAN.md D14): the
 * language they chose last time follows them to a new browser.
 *
 * <p>It writes the same language cookie that the {@code ?lang=} switch writes, so from then on the
 * page is in that language until the person switches again. A stored value this version does not
 * support falls back to Hungarian, the household's default.
 */
@Component
class SignInLanguage {

  private final LocaleResolver resolver;

  /**
   * Creates the helper.
   *
   * @param resolver the cookie-based locale resolver of the application
   */
  SignInLanguage(LocaleResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * Sets the language cookie to the person's stored language.
   *
   * @param request the sign-in callback request
   * @param response the response that receives the cookie
   * @param user the person who just signed in, with the language stored at their last switch
   */
  void apply(HttpServletRequest request, HttpServletResponse response, SignedInUser user) {
    Locale stored = SupportedLocales.parse(user.locale()).orElse(SupportedLocales.HUNGARIAN);
    resolver.setLocale(request, response, stored);
  }
}
