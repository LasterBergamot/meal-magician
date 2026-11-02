package hu.lasterbergamot.mealmagician.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Switches the language: {@code POST /language} with {@code lang=hu} or {@code lang=en}.
 *
 * <p>The choice is stored by the request's {@link LocaleResolver} (a cookie, so it also works on
 * the sign-in and error pages) and, for a signed-in person, saved through the {@link
 * LanguageStore}, so it applies again after their next sign-in on any device. Unsupported codes are
 * ignored on purpose: Spring's own {@code LocaleChangeInterceptor} would accept any locale.
 *
 * <p>Why a POST and not a {@code ?lang=} link: the switch writes a stored preference, and a GET
 * that writes can be triggered by any other site's link or image. A POST goes through the CSRF
 * check like every other change. The path is public ({@code SecurityConfig.PUBLIC_PATHS}) because
 * anonymous visitors switch language too; the CSRF token still protects it.
 */
@Controller
public class LanguageController {

  /** Where the switch sends the browser when it did not come from a page of this application. */
  static final String FALLBACK = "/";

  private final LocaleResolver localeResolver;
  private final ObjectProvider<LanguageStore> stores;

  /**
   * Creates the controller.
   *
   * @param localeResolver the cookie resolver of {@link I18nConfig}
   * @param stores the store that saves the choice for signed-in people; it may be absent in a test
   *     slice, in which case only the cookie changes
   */
  public LanguageController(LocaleResolver localeResolver, ObjectProvider<LanguageStore> stores) {
    this.localeResolver = localeResolver;
    this.stores = stores;
  }

  /**
   * Applies the requested language, if it is a supported one, and sends the browser back to the
   * page the switcher was on.
   *
   * @param code the language code from the switcher button
   * @param request the current request; its {@code Referer} says which page to return to
   * @param response the response, which receives the language cookie
   * @return {@code 303 See Other} to the page the person came from (path and query only), or to
   *     {@code /} when the {@code Referer} is missing or not from this application
   */
  @PostMapping("/language")
  public ResponseEntity<Void> switchLanguage(
      @RequestParam("lang") String code, HttpServletRequest request, HttpServletResponse response) {
    SupportedLocales.parse(code)
        .ifPresent(locale -> {
          localeResolver.setLocale(request, response, locale);
          LanguageStore store = stores.getIfAvailable();
          if (store != null) {
            store.remember(request, locale);
          }
        });
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .header(HttpHeaders.LOCATION, returnPath(request))
        .build();
  }

  /**
   * The same-origin path (with query) from the {@code Referer} header, or {@value #FALLBACK}.
   *
   * <p>Only the path and query are used, never the scheme or host, so a forged {@code Referer}
   * cannot turn the redirect into one to another site. A path that does not start with a single
   * slash (for example {@code //evil.example}, which a browser reads as another host) is refused. A
   * raw backslash cannot get through: {@link URI} rejects it, and an encoded one stays encoded.
   */
  private static String returnPath(HttpServletRequest request) {
    String referer = request.getHeader(HttpHeaders.REFERER);
    if (referer == null) {
      return FALLBACK;
    }
    try {
      URI uri = new URI(referer);
      String path = uri.getRawPath();
      boolean sameHost = request.getServerName().equalsIgnoreCase(uri.getHost());
      boolean localPath = path != null && path.startsWith("/") && !path.startsWith("//");
      if (!sameHost || !localPath) {
        return FALLBACK;
      }
      return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    } catch (URISyntaxException e) {
      return FALLBACK;
    }
  }
}
