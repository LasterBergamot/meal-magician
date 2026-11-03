package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.planning.SharedWeek;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import hu.lasterbergamot.mealmagician.shared.security.RateLimiter;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import hu.lasterbergamot.mealmagician.sharing.ShareLinkService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The public, read-only page of a share link: {@code GET /share/{token}}. It is the only business
 * route that needs no sign-in, so every rule here comes from PLAN.md D28 and none may be relaxed
 * without changing that decision.
 *
 * <ul>
 *   <li><b>Throttle first.</b> One global limiter answers 429 with {@code Retry-After} before
 *       anything else happens, so a flood of guesses costs neither a database call nor a template.
 *   <li><b>One answer for every bad token.</b> A malformed, unknown, expired or revoked token gets
 *       the same status, the same template and the same headers, because a different answer would
 *       tell a guesser which tokens exist. A malformed token is refused before the database is
 *       asked.
 *   <li><b>No state.</b> Nothing is written: no counter, no session, no cookie. The page has no
 *       form and no CSRF token ({@code SecurityConfig.csrfMetaSource} skips {@code /share/}).
 *   <li><b>The token stays out.</b> It is never logged, never put in the model, never rendered and
 *       never sent on in a {@code Referer} ({@code Referrer-Policy: no-referrer}).
 * </ul>
 */
@Controller
class PublicShareController {

  private final ShareLinkService shareLinks;
  private final RateLimiter limiter;
  private final Messages messages;

  PublicShareController(
      ShareLinkService shareLinks, RateLimiter shareRateLimiter, Messages messages) {
    this.shareLinks = shareLinks;
    this.limiter = shareRateLimiter;
    this.messages = messages;
  }

  /**
   * Shows the shared week for a token.
   *
   * @param token the last path segment; untrusted text, checked with {@link
   *     TokenCodes#isWellFormed} before it is used for anything
   * @param response receives the status and the privacy headers
   * @param model receives the {@link SharedWeekPage} (never the token)
   * @return the week page with status 200; the not-found page with status 404 for any token that
   *     does not open an active link; the same bare page with status 429 and {@code Retry-After}
   *     when the global throttle is used up
   */
  @GetMapping("/share/{token}")
  String open(@PathVariable String token, HttpServletResponse response, Model model) {
    privacyHeaders(response);
    if (!limiter.tryAcquire()) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", String.valueOf(limiter.retryAfterSeconds()));
      return refusal(model, "share.public.throttled.title", "share.public.throttled.message");
    }
    Optional<SharedWeek> week =
        TokenCodes.isWellFormed(token) ? shareLinks.open(token) : Optional.empty();
    if (week.isEmpty()) {
      response.setStatus(HttpStatus.NOT_FOUND.value());
      return refusal(model, "share.public.notFound.title", "share.public.notFound.message");
    }
    model.addAttribute("page", SharedWeekPage.of(week.get(), messages));
    return "sharing/week";
  }

  /**
   * The headers every answer of this route carries, set first so the 404 and the 429 have them too.
   * {@code Referrer-Policy} replaces the app-wide {@code same-origin} that {@code
   * SecurityHeadersFilter} wrote earlier, and a response that already has {@code Cache-Control}
   * keeps it (Spring Security only adds its own when none is set).
   */
  private static void privacyHeaders(HttpServletResponse response) {
    response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Cache-Control", "no-store");
  }

  /** The one template of every refusal; only the message keys differ between 404 and 429. */
  private static String refusal(Model model, String titleKey, String messageKey) {
    model.addAttribute("titleKey", titleKey);
    model.addAttribute("messageKey", messageKey);
    return "sharing/not_found";
  }
}
