package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the browser security headers to every response: a Content-Security-Policy without {@code
 * unsafe-inline} or {@code unsafe-eval}, {@code X-Content-Type-Options} and {@code
 * Referrer-Policy}.
 *
 * <p>Why a plain filter: Spring Security does not exist yet (issue #12). Once it does, move {@link
 * #CONTENT_SECURITY_POLICY} into {@code http.headers(...)} (security.md lists the target header
 * set, including HSTS in prod) and delete this class. It is deliberately small and self-contained
 * so that move is mechanical.
 *
 * <p>What the policy means for developers: only files from our own origin may load, there are no
 * inline {@code <script>} blocks, inline event handlers ({@code onclick=...}) or {@code style=}
 * attributes, and the Alpine.js CSP build is required because the normal build evaluates strings as
 * code. A template guard test enforces the same rules before the browser has to.
 */
@Component
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
