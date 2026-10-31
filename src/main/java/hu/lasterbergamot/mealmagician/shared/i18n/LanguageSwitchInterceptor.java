package hu.lasterbergamot.mealmagician.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Switches the language when a request carries {@code ?lang=hu} or {@code ?lang=en}.
 *
 * <p>The choice is stored by the request's {@link LocaleResolver} (a cookie) and applies to the
 * same request too, so the page that follows the switcher link is already in the new language.
 * Unsupported codes are ignored on purpose: Spring's own {@code LocaleChangeInterceptor} would
 * accept any locale.
 */
public class LanguageSwitchInterceptor implements HandlerInterceptor {

  /** The query parameter the language switcher links use. */
  public static final String PARAMETER = "lang";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    SupportedLocales.parse(request.getParameter(PARAMETER))
        .ifPresent(locale -> {
          LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request);
          resolver.setLocale(request, response, locale);
        });
    return true;
  }
}
