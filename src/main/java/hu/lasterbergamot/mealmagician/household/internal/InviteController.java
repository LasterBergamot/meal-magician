package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CreatedInvite;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.InvalidInviteEmailException;
import hu.lasterbergamot.mealmagician.household.InviteExpiry;
import hu.lasterbergamot.mealmagician.household.InviteService;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The owner's invite section on the households page: create an invite link, revoke a pending one.
 *
 * <p>Both actions work on the household the person is currently in (the {@link CurrentHousehold}
 * parameter, re-checked against their memberships on every request), and the service refuses anyone
 * who is not its owner.
 *
 * <p>Both actions answer with the invite section again ({@code household/_invites}) so htmx can
 * swap it in place; a normal request gets the whole page. Creating an invite is the only response
 * that carries the link, because the code is not stored and cannot be shown a second time.
 */
@Controller
@RequestMapping("/households/invites")
class InviteController {

  private static final String INVALID_EMAIL = "household.invite.invalid";

  private final InviteService invites;
  private final HouseholdsPageFactory pages;

  InviteController(InviteService invites, HouseholdsPageFactory pages) {
    this.invites = invites;
    this.pages = pages;
  }

  /**
   * Creates an invite and shows its link once.
   *
   * @param user the signed-in person
   * @param household the household the person works in, which the invite is for
   * @param email the address to invite
   * @param expiry the chosen lifetime code ({@code 2h}, {@code 24h}, {@code 3d})
   * @param request the current request, which tells the host the link must point at
   * @param response the response
   * @param model receives the page model
   * @return the invite section, or the whole page for a normal request; status 422 for a bad
   *     address
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException (404) if the person
   *     is not a member
   * @throws hu.lasterbergamot.mealmagician.household.OwnerOnlyException (403) if they are not an
   *     owner
   */
  @PostMapping
  String create(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(defaultValue = "") String email,
      @RequestParam(defaultValue = "") String expiry,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    HouseholdsPage page;
    try {
      CreatedInvite created =
          invites.create(household.id(), user.userId(), email, InviteExpiry.fromCode(expiry));
      String link =
          ServletUriComponentsBuilder.fromContextPath(request)
              .path("/join/{code}")
              .buildAndExpand(created.code())
              .toUriString();
      // Built after the invite exists, so the list already contains it.
      page = pages.page(user, request);
      page = page.withInvites(page.invites().created(link, created.invite().email()));
    } catch (InvalidInviteEmailException e) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      page = pages.page(user, request);
      page = page.withInvites(page.invites().rejected(email, expiry, INVALID_EMAIL));
    }
    model.addAttribute("page", page);
    return Htmx.view(request, response, "household/_invites", "household/page");
  }

  /**
   * Revokes a pending invite.
   *
   * @param user the signed-in person
   * @param household the household the person works in
   * @param inviteId the invite to revoke
   * @param request the current request
   * @param response the response
   * @param model receives the page model
   * @return the invite section, or the whole page for a normal request
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException (404) if the person
   *     is not a member
   * @throws hu.lasterbergamot.mealmagician.household.OwnerOnlyException (403) if they are not an
   *     owner
   * @throws hu.lasterbergamot.mealmagician.household.InviteNotFoundException (404) if the invite is
   *     not in this household
   */
  @PostMapping("/{inviteId}/revoke")
  String revoke(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long inviteId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    invites.revoke(household.id(), user.userId(), inviteId);
    model.addAttribute("page", pages.page(user, request));
    return Htmx.view(request, response, "household/_invites", "household/page");
  }
}
