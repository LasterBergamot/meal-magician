package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the browser security headers to every response: a Content-Security-Policy without {@code
 * unsafe-inline} or {@code unsafe-eval}, {@code X-Content-Type-Options} and {@code
 * Referrer-Policy}.
 *
 * <p>Why a plain filter and not {@code http.headers(...)}: it runs <em>before</em> the Spring
 * Security chain ({@link Ordered#HIGHEST_PRECEDENCE}), so even the redirect to the login page and
 * the 401/403 answers carry the policy, and the {@code shared} module needs no Spring Security
 * dependency. Spring Security adds the remaining headers ({@code X-Frame-Options}, HSTS on HTTPS).
 *
 * <p>What the policy means for developers: only files from our own origin may load, there are no
 * inline {@code <script>} blocks, inline event handlers ({@code onclick=...}) or {@code style=}
 * attributes, and the Alpine.js CSP build is required because the normal build evaluates strings as
 * code. A template guard test enforces the same rules before the browser has to.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  /**
   * The policy. {@code form-action 'self'} means forms may only post to us; sign-in with Google is
   * a link, not a form post, so it is not affected.
   */
  public static final String CONTENT_SECURITY_POLICY =
      String.join(
          "; ",
          "default-src 'self'",
          "script-src 'self'",
          "style-src 'self'",
          "img-src 'self' data:",
          "font-src 'self'",
          "connect-src 'self'",
          "manifest-src 'self'",
          "worker-src 'self'",
          "object-src 'none'",
          "base-uri 'self'",
          "form-action 'self'",
          "frame-ancestors 'none'");

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    // The error page is rendered in a second dispatch; make sure it carries the headers too.
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "same-origin");
    chain.doFilter(request, response);
  }
}
