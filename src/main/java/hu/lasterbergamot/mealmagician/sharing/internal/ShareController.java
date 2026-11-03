package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.household.MemberView;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import hu.lasterbergamot.mealmagician.sharing.CreatedShareLink;
import hu.lasterbergamot.mealmagician.sharing.ShareLimitException;
import hu.lasterbergamot.mealmagician.sharing.ShareLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The members' side of share links: the sheet on the plan page where a link is made, copied and
 * revoked. The public side (opening a link without signing in) is {@link PublicShareController}.
 *
 * <p>Rules shared by all routes (PLAN.md D28):
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold} first, so a person in no household gets 404
 *       before anything else. Any member may create and revoke; nothing here is owner-only.
 *   <li>A link of another household is 404 on revoke ({@code ShareLinkNotFoundException}).
 *   <li>The raw token appears in exactly one response, the one that creates the link, and only in
 *       its body: never in a redirect, a flash value, the session or a log line. That response also
 *       carries {@code Cache-Control: no-store}, so no cache keeps the page that holds the secret.
 *   <li>An unknown {@code expiry} gives the default of 7 days, and a bad {@code week} the current
 *       week ({@link ShareForm}).
 * </ul>
 */
@Controller
@RequestMapping("/plan/share")
class ShareController {

  private static final String LIMIT_MESSAGE = "share.limit";

  private final ShareLinkService shares;
  private final MemberService members;
  private final Messages messages;
  private final Clock clock;

  ShareController(ShareLinkService shares, MemberService members, Messages messages, Clock clock) {
    this.shares = shares;
    this.members = members;
    this.messages = messages;
    this.clock = clock;
  }

  /**
   * Shows the share sheet's content for one week: the create form and the week's links.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param week the week code, for example {@code 2026-W45}; missing or malformed means the current
   *     week
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link SharePanel}
   * @return the sheet's content for htmx, otherwise a whole page
   */
  @GetMapping
  String show(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    ShareForm form = ShareForm.of(week, null, clock);
    model.addAttribute("panel", panel(user, household, form));
    return Htmx.view(request, response, "sharing/_share", "sharing/share");
  }

  /**
   * Makes a share link and shows its address once.
   *
   * @param user the signed-in person
   * @param household the household whose week is shared
   * @param week the week code; missing or malformed means the current week
   * @param expiry {@code 1d}, {@code 7d} or {@code 30d}; anything else means 7 days
   * @param request the current request, which tells the host and scheme the address must use
   * @param response the response; it gets {@code Cache-Control: no-store} because the body holds
   *     the secret
   * @param model receives the {@link SharePanel}
   * @return the sheet's content (htmx) or the whole page, listing the new link; with status 422 and
   *     a message instead when the household already has the most active links
   */
  @PostMapping
  String create(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      @RequestParam(required = false) String expiry,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    response.setHeader("Cache-Control", "no-store");
    ShareForm form = ShareForm.of(week, expiry, clock);
    SharePanel panel;
    try {
      CreatedShareLink created =
          shares.create(user.userId(), household.id(), form.monday(), form.expiry());
      // Built after the link exists, so the list already contains it. The base comes from the
      // request, so the forwarded scheme and host of Cloud Run's proxy apply.
      String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
      panel = panel(user, household, form).created(SharePanel.linkFor(base, created.token()));
    } catch (ShareLimitException full) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      panel = panel(user, household, form).rejected(LIMIT_MESSAGE, form.expiry().code());
    }
    model.addAttribute("panel", panel);
    return Htmx.view(request, response, "sharing/_share", "sharing/share");
  }

  /**
   * Withdraws a link. Any member may do it, and revoking one that is already revoked is not an
   * error.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param linkId the link to revoke
   * @param week the week code the sheet shows, so the refreshed list is the same week's
   * @param request the current request
   * @param response the response
   * @param model receives the {@link SharePanel}
   * @return the refreshed list section (htmx), which replaces the whole list and so closes the
   *     confirm dialog, or the whole page
   * @throws hu.lasterbergamot.mealmagician.sharing.ShareLinkNotFoundException (404) if the link is
   *     not in this household
   */
  @PostMapping("/{linkId}/revoke")
  String revoke(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long linkId,
      @RequestParam(required = false) String week,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    shares.revoke(user.userId(), household.id(), linkId);
    ShareForm form = ShareForm.of(week, null, clock);
    model.addAttribute("panel", panel(user, household, form));
    return Htmx.view(request, response, "sharing/_share_list", "sharing/share");
  }

  private SharePanel panel(SignedInUser user, CurrentHousehold household, ShareForm form) {
    LocalDate monday = form.monday();
    Map<Long, String> names =
        members.list(household.id(), user.userId()).stream()
            .collect(Collectors.toMap(MemberView::userId, MemberView::displayName));
    List<ShareRow> rows =
        shares.list(user.userId(), household.id(), monday).stream()
            .map(link -> ShareRow.of(link, names, clock.getZone()))
            .toList();
    return new SharePanel(
        ShareForm.codeOf(monday),
        messages.t("plan.week.title", String.valueOf(ShareForm.numberOf(monday))),
        messages.t(
            "plan.week.range",
            ShareDates.mediumDate(monday, messages.locale()),
            ShareDates.mediumDate(monday.plusDays(6), messages.locale())),
        rows,
        form.expiry().code(),
        null,
        null);
  }
}
