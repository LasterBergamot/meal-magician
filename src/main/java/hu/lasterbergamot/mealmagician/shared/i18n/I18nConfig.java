package hu.lasterbergamot.mealmagician.shared.i18n;

import hu.lasterbergamot.mealmagician.shared.ClockConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.TimeZone;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/**
 * Wires up translations: the message bundles, the {@link Messages} helper and a cookie that
 * remembers the visitor's language.
 *
 * <p>The cookie ({@value #COOKIE_NAME}) is a stop-gap: it works before anyone is signed in. When
 * {@code app_user} exists (issue #12) the language is stored per user in the database and this
 * resolver stays as the fallback for signed-out pages.
 *
 * <p>The message source is declared here instead of relying on Spring Boot's auto-configuration,
 * because that only switches on when a base {@code messages.properties} exists; we only have {@code
 * messages_hu} and {@code messages_en}.
 */
@Configuration(proxyBeanMethods = false)
public class I18nConfig implements WebMvcConfigurer {

  /** Name of the cookie that stores the chosen language ({@code hu} or {@code en}). */
  public static final String COOKIE_NAME = "mm_lang";

  /**
   * The bundles {@code messages_hu.properties} and {@code messages_en.properties}.
   *
   * @return a UTF-8 message source that never falls back to the server's own locale
   */
  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // Without this, a missing English text would silently turn into the server's system language.
    source.setFallbackToSystemLocale(false);
    return source;
  }

  /**
   * The {@code t("key")} helper handed to templates.
   *
   * @param messageSource the bundles to read from
   * @return the helper
   */
  @Bean
  public Messages messages(MessageSource messageSource) {
    return new Messages(messageSource);
  }

  /**
   * Stores the language in a one-year cookie. Hungarian is the default, so the browser's {@code
   * Accept-Language} is deliberately ignored.
   *
   * <p>The bean must be called {@code localeResolver}: that is the name Spring MVC looks for.
   *
   * @return the resolver
   */
  @Bean
  public LocaleResolver localeResolver() {
    CookieLocaleResolver resolver = new CookieLocaleResolver(COOKIE_NAME);
    resolver.setDefaultLocale(SupportedLocales.HUNGARIAN);
    resolver.setDefaultTimeZone(TimeZone.getTimeZone(ClockConfig.ZONE));
    resolver.setCookieMaxAge(Duration.ofDays(365));
    resolver.setCookiePath("/");
    resolver.setCookieHttpOnly(true);
    resolver.setCookieSameSite("Lax");
    return resolver;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LanguageSwitchInterceptor());
  }
}
