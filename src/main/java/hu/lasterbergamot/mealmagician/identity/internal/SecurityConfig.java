package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.shared.web.CsrfMeta;
import hu.lasterbergamot.mealmagician.shared.web.CsrfMetaSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

/**
 * The security rules for the whole app: nothing is reachable without signing in, except the short
 * list in {@link #PUBLIC_PATHS}.
 *
 * <p>Sign-in is Google OIDC ({@code oauth2Login}); who may stay is decided by {@link
 * AllowlistedOidcUserService}. CSRF protection stays on: htmx sends the token as the {@code
 * X-CSRF-TOKEN} header taken from a {@code <meta>} tag in the layout. Sessions are server-side in
 * the database (Spring Session JDBC, PLAN.md D7).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfig {

  /** The login page, shown to anonymous visitors. */
  static final String LOGIN_URL = "/login";

  /**
   * Paths anyone may open without signing in. Keep this list short and explicit; a test walks every
   * mapped endpoint and fails if anything else is reachable anonymously (security.md).
   */
  static final String[] PUBLIC_PATHS = {
    LOGIN_URL,
    "/error",
    "/actuator/health/**",
    "/css/**",
    "/js/**",
    "/images/**",
    "/icons/**",
    "/favicon.ico",
    "/manifest.webmanifest",
    "/sw.js"
  };

  /**
   * Builds the one filter chain.
   *
   * @param http Spring Security's builder
   * @param oidcUserService the allowlist gate that runs after Google confirms who the person is
   * @param failureHandler turns a refused or failed sign-in into a 403 or a redirect
   * @param successHandler clears expired sessions, then goes to the originally requested page
   * @return the filter chain
   * @throws Exception if Spring Security rejects the configuration
   */
  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      AllowlistedOidcUserService oidcUserService,
      SignInFailureHandler failureHandler,
      SessionCleanupOnSignIn successHandler)
      throws Exception {
    return http.authorizeHttpRequests(requests ->
            requests.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .oauth2Login(login ->
            login
                .loginPage(LOGIN_URL)
                .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                .successHandler(successHandler)
                .failureHandler(failureHandler))
        .logout(logout -> logout.logoutSuccessUrl(LOGIN_URL + "?logout"))
        .exceptionHandling(handling ->
            // Both are "mappings", tried in order. An explicit authenticationEntryPoint(...)
            // would override every mapping, so the htmx rule would never run.
            handling
                .defaultAuthenticationEntryPointFor(
                    new HtmxAuthenticationEntryPoint(LOGIN_URL),
                    new RequestHeaderRequestMatcher("HX-Request"))
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint(LOGIN_URL), AnyRequestMatcher.INSTANCE))
        .headers(headers ->
            headers
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
        .build();
  }

  /**
   * Hands the CSRF token of the current request to the layout, which renders it as {@code <meta>}
   * tags for htmx to send back as a header.
   *
   * @return the source; it returns {@code null} when Spring Security gave the request no token
   */
  @Bean
  CsrfMetaSource csrfMetaSource() {
    return request -> {
      CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      return token == null ? null : new CsrfMeta(token.getHeaderName(), token.getToken());
    };
  }
}
