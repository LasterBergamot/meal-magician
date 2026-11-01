package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.InvitePreview;
import hu.lasterbergamot.mealmagician.household.InviteProblem;
import hu.lasterbergamot.mealmagician.household.InviteRefusedException;
import hu.lasterbergamot.mealmagician.household.InviteService;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

/**
 * The page an invite link opens, {@code /join/<code>}, and the button on it.
 *
 * <p>The page needs a signed-in person like every other page: an anonymous visitor is sent to the
 * login page and comes back here after signing in (Spring Security remembers the requested URL).
 * Opening the link only <em>shows</em> the household; joining is a separate POST, so a link
 * previewer or a prefetching browser cannot use the invite up by merely fetching the URL.
 *
 * <p>The code is in the URL, so it can appear in access logs of whatever sits in front of the app.
 * That is why it is single-use, expires and only works for the invited Google account.
 */
@Controller
class JoinController {

  private static final String PAGE = "household/join";

  private final InviteService invites;
  private final CurrentHouseholdResolver resolver;

  JoinController(InviteService invites, CurrentHouseholdResolver resolver) {
    this.invites = invites;
    this.resolver = resolver;
  }

  /**
   * Shows which household the link is for, or why it cannot be used. The status tells which: {@code
   * 404} for an unknown code, {@code 403} for another Google account, {@code 410} for a link that
   * was revoked, used or has expired.
   *
   * @param user the signed-in person
   * @param code the code from the URL
   * @param response the response, whose status reflects a refusal
   * @param model receives the {@link JoinPage}
   * @return the join page
   */
  @GetMapping("/join/{code}")
  String page(
      @AuthenticationPrincipal SignedInUser user,
      @PathVariable String code,
      HttpServletResponse response,
      Model model) {
    try {
      InvitePreview preview = invites.preview(code, user.getEmail());
      model.addAttribute("join", new JoinPage(code, preview.householdName(), null));
    } catch (InviteRefusedException e) {
      response.setStatus(statusOf(e.getProblem()));
      model.addAttribute(
          "join",
          new JoinPage(null, null, "join.error." + e.getProblem().name().toLowerCase(Locale.ROOT)));
    }
    return PAGE;
  }

  /**
   * Joins the household as a member, makes it the one the person works in, and goes home. If the
   * invite stopped being usable since the page was shown, the person is sent back to the link,
   * which then explains why.
   *
   * @param user the signed-in person
   * @param code the code the confirm button posts
   * @param request the current request
   * @param response the response
   * @return a redirect
   */
  @PostMapping("/join")
  String join(
      @AuthenticationPrincipal SignedInUser user,
      @RequestParam(defaultValue = "") String code,
      HttpServletRequest request,
      HttpServletResponse response) {
    try {
      long householdId = invites.join(code, user.userId(), user.getEmail());
      resolver.select(request, householdId);
      return Htmx.redirect(request, response, "/households");
    } catch (InviteRefusedException e) {
      return Htmx.redirect(
          request, response, "/join/" + UriUtils.encodePathSegment(code, StandardCharsets.UTF_8));
    }
  }

  private static int statusOf(InviteProblem problem) {
    return switch (problem) {
      case UNKNOWN -> HttpServletResponse.SC_NOT_FOUND;
      case WRONG_EMAIL -> HttpServletResponse.SC_FORBIDDEN;
      case REVOKED, USED, EXPIRED -> HttpServletResponse.SC_GONE;
    };
  }
}
