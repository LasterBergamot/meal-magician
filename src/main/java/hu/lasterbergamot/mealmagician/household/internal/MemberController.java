package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.MemberRefusedException;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The members page of the household the person is in: everyone can see who is in it and leave;
 * owners can also remove people and hand the household over.
 *
 * <p>All actions work on the {@link CurrentHousehold} (re-checked against the person's memberships
 * on every request), and the service refuses anyone who is not an owner. Remove and hand-over
 * answer with the members section again so htmx swaps it in place; a refused action does the same
 * with status 409 and the reason. Leaving ends on the households page.
 */
@Controller
@RequestMapping("/households/members")
class MemberController {

  private final MemberService members;
  private final HouseholdService households;

  MemberController(MemberService members, HouseholdService households) {
    this.members = members;
    this.households = households;
  }

  /**
   * Shows the household's members.
   *
   * @param user the signed-in person
   * @param household the household the person works in
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link MembersPage}
   * @return the page, or its section for htmx requests
   */
  @GetMapping
  String page(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    return show(user, household.id(), null, request, response, model);
  }

  /**
   * Removes a member. Owners only.
   *
   * @param user the signed-in person
   * @param household the household the person works in
   * @param memberId the {@code app_user} id of the person to remove
   * @param request the current request
   * @param response the response
   * @param model receives the page model
   * @return the members section, or the whole page for a normal request; status 409 if refused
   * @throws hu.lasterbergamot.mealmagician.household.OwnerOnlyException (403) if the person is not
   *     an owner
   * @throws hu.lasterbergamot.mealmagician.household.MemberNotFoundException (404) if the other
   *     person is not in this household
   */
  @PostMapping("/{memberId}/remove")
  String remove(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long memberId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      members.remove(household.id(), user.userId(), memberId);
      return show(user, household.id(), null, request, response, model);
    } catch (MemberRefusedException e) {
      return refused(user, household.id(), e, request, response, model);
    }
  }

  /**
   * Hands the household over to another member. Owners only.
   *
   * @param user the signed-in person
   * @param household the household the person works in
   * @param memberId the {@code app_user} id of the member who takes over
   * @param request the current request
   * @param response the response
   * @param model receives the page model
   * @return the members section, or the whole page for a normal request; status 409 if refused
   * @throws hu.lasterbergamot.mealmagician.household.OwnerOnlyException (403) if the person is not
   *     an owner
   * @throws hu.lasterbergamot.mealmagician.household.MemberNotFoundException (404) if the other
   *     person is not in this household
   */
  @PostMapping("/{memberId}/transfer")
  String transfer(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long memberId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      members.transferOwnership(household.id(), user.userId(), memberId);
      return show(user, household.id(), null, request, response, model);
    } catch (MemberRefusedException e) {
      return refused(user, household.id(), e, request, response, model);
    }
  }

  /**
   * Leaves the household the person is in. The last owner cannot.
   *
   * @param user the signed-in person
   * @param household the household the person works in
   * @param request the current request
   * @param response the response
   * @param model receives the page model when the last owner is refused
   * @return a redirect to the households page, or the members section with status 409 and the
   *     reason
   */
  @PostMapping("/leave")
  String leave(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      members.leave(household.id(), user.userId());
      return Htmx.redirect(request, response, "/households");
    } catch (MemberRefusedException e) {
      return refused(user, household.id(), e, request, response, model);
    }
  }

  private String refused(
      SignedInUser user,
      long householdId,
      MemberRefusedException refusal,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    response.setStatus(HttpServletResponse.SC_CONFLICT);
    return show(user, householdId, refusal.problem().messageKey(), request, response, model);
  }

  /**
   * Builds the page from the database, not from the request's cached household: the viewer's own
   * role may have just changed.
   */
  private String show(
      SignedInUser user,
      long householdId,
      String error,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    CurrentHousehold fresh = households.require(householdId, user.userId());
    boolean viewerIsOwner = fresh.role() == HouseholdRole.OWNER;
    var rows =
        members.list(householdId, user.userId()).stream()
            .map(member -> {
              boolean self = member.userId() == user.userId();
              return new MemberRow(
                  member.userId(),
                  member.displayName(),
                  member.email(),
                  member.role() == HouseholdRole.OWNER,
                  self,
                  viewerIsOwner && !self);
            })
            .toList();
    model.addAttribute("page", new MembersPage(fresh, rows, error));
    return Htmx.view(request, response, "household/_members", "household/members");
  }
}
