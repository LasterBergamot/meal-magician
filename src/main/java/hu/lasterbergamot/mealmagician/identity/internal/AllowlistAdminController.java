package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The allowlist page for instance admins (PLAN.md D6): see who may sign in, add an address, remove
 * one, without a redeploy.
 *
 * <p>Only people with the admin role reach {@code /admin/**}: {@link SecurityConfig} answers
 * everyone else with 403 before a controller runs. Every action answers with the list section so
 * htmx swaps it in place; a normal request gets the whole page. A refused change shows its reason
 * in the section: 422 for a bad or already-listed address, 409 for removing a bootstrap admin.
 */
@Controller
@RequestMapping("/admin/allowlist")
class AllowlistAdminController {

  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final AllowlistAdminService allowlist;
  private final Clock clock;

  AllowlistAdminController(AllowlistAdminService allowlist, Clock clock) {
    this.allowlist = allowlist;
    this.clock = clock;
  }

  /**
   * Shows the allowlist.
   *
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link AllowlistPage}
   * @return the page, or its section for htmx requests
   */
  @GetMapping
  String page(HttpServletRequest request, HttpServletResponse response, Model model) {
    return show("", null, request, response, model);
  }

  /**
   * Lets an address sign in.
   *
   * @param admin the signed-in admin
   * @param email the address to add, in any case
   * @param request the current request
   * @param response the response
   * @param model receives the page model
   * @return the list section, or the whole page for a normal request; status 422 if the address is
   *     not valid or is already allowed
   */
  @PostMapping
  String add(
      @AuthenticationPrincipal SignedInUser admin,
      @RequestParam(defaultValue = "") String email,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      allowlist.add(email, admin.userId());
      return show("", null, request, response, model);
    } catch (AllowlistRefusedException e) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      return show(email, e.problem().messageKey(), request, response, model);
    }
  }

  /**
   * Stops an address from signing in and ends the person's live sessions, so they are sent to the
   * sign-in page on their next request (#59).
   *
   * @param email the address to remove
   * @param request the current request
   * @param response the response
   * @param model receives the page model
   * @return the list section, or the whole page for a normal request; status 409 for a bootstrap
   *     admin
   */
  @PostMapping("/remove")
  String remove(
      @RequestParam(defaultValue = "") String email,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      allowlist.remove(email);
      return show("", null, request, response, model);
    } catch (AllowlistRefusedException e) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      return show("", e.problem().messageKey(), request, response, model);
    }
  }

  private String show(
      String typedEmail,
      String error,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    var format = WHEN.withZone(clock.getZone());
    var rows =
        allowlist.list().stream()
            .map(entry ->
                new AllowlistRow(
                    entry.email(),
                    entry.origin().messageKey(),
                    entry.addedAt() == null ? "" : format.format(entry.addedAt()),
                    entry.removable()))
            .toList();
    model.addAttribute("page", new AllowlistPage(rows, typedEmail, error));
    return Htmx.view(request, response, "admin/_allowlist", "admin/allowlist");
  }
}
