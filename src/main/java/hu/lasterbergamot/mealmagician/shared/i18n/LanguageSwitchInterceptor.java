package hu.lasterbergamot.mealmagician.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Switches the language when a request carries {@code ?lang=hu} or {@code ?lang=en}.
 *
 * <p>The choice is stored by the request's {@link LocaleResolver} (a cookie) and applies to the
 * same request too, so the page that follows the switcher link is already in the new language. For
 * a signed-in person it is also saved through the {@link LanguageStore}, so the choice applies
 * again after their next sign-in on any device. Unsupported codes are ignored on purpose: Spring's
 * own {@code LocaleChangeInterceptor} would accept any locale.
 */
public class LanguageSwitchInterceptor implements HandlerInterceptor {

  /** The query parameter the language switcher links use. */
  public static final String PARAMETER = "lang";

  private final ObjectProvider<LanguageStore> stores;

  /**
   * Creates the interceptor.
   *
   * @param stores the store that saves the choice for signed-in people; it may be absent in a test
   *     slice, in which case only the cookie changes
   */
  public LanguageSwitchInterceptor(ObjectProvider<LanguageStore> stores) {
    this.stores = stores;
  }

  /**
   * Applies the requested language, if the parameter names a supported one.
   *
   * @param request the current request
   * @param response the response, which receives the language cookie
   * @param handler the handler the request is for (not used)
   * @return always {@code true}: the request continues
   */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    SupportedLocales.parse(request.getParameter(PARAMETER))
        .ifPresent(locale -> {
          RequestContextUtils.getLocaleResolver(request).setLocale(request, response, locale);
          LanguageStore store = stores.getIfAvailable();
          if (store != null) {
            store.remember(request, locale);
          }
        });
    return true;
  }
}
