package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdSummary;
import hu.lasterbergamot.mealmagician.household.InvalidHouseholdNameException;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The households page: list my households, create one, switch to another, rename the current one.
 *
 * <p>Forms are htmx posts. A rejected form answers {@code 422} (or {@code 409} for an edit
 * conflict) with just that form's fragment, so the page keeps its place; a successful post sends
 * the browser to the next page with {@link Htmx#redirect}.
 */
@Controller
@RequestMapping("/households")
class HouseholdController {

  private static final String NAME_INVALID = "household.name.invalid";
  private static final String CONFLICT = "household.conflict";

  private final HouseholdService households;
  private final CurrentHouseholdResolver resolver;

  HouseholdController(HouseholdService households, CurrentHouseholdResolver resolver) {
    this.households = households;
    this.resolver = resolver;
  }

  /**
   * Shows the person's households, with the forms to create one and rename the current one.
   *
   * @param user the signed-in person
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link HouseholdsPage}
   * @return the page, or its list part for htmx requests
   */
  @GetMapping
  String page(
      @AuthenticationPrincipal SignedInUser user,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    fill(model, user, request, "", null, null, null);
    return Htmx.view(request, response, "household/_content", "household/page");
  }

  /**
   * Creates a household owned by the person and switches to it.
   *
   * @param user the signed-in person
   * @param name the wanted name
   * @param request the current request
   * @param response the response
   * @param model receives the page model when the name is rejected
   * @return a redirect to the households page, or the create form with a 422 for a bad name
   */
  @PostMapping
  String create(
      @AuthenticationPrincipal SignedInUser user,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      HouseholdSummary created = households.create(user.userId(), name);
      resolver.select(request, created.id());
      return Htmx.redirect(request, response, "/households");
    } catch (InvalidHouseholdNameException e) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      fill(model, user, request, name, NAME_INVALID, null, null);
      return Htmx.view(request, response, "household/_create", "household/page");
    }
  }

  /**
   * Makes a household the one the person works in.
   *
   * @param user the signed-in person
   * @param id the household to switch to
   * @param request the current request
   * @param response the response
   * @return a redirect to the home page
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException (404) if the person
   *     is not a member
   */
  @PostMapping("/{id}/switch")
  String switchTo(
      @AuthenticationPrincipal SignedInUser user,
      @PathVariable long id,
      HttpServletRequest request,
      HttpServletResponse response) {
    households.require(id, user.userId());
    resolver.select(request, id);
    return Htmx.redirect(request, response, "/");
  }

  /**
   * Renames a household. Owners only.
   *
   * @param user the signed-in person
   * @param id the household to rename
   * @param name the wanted name
   * @param request the current request
   * @param response the response
   * @param model receives the page model when the rename is rejected
   * @return a redirect to the households page, or the rename form with a 422 (bad name) or 409
   *     (someone else renamed it first)
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException (404) if the person
   *     is not a member
   * @throws hu.lasterbergamot.mealmagician.household.OwnerOnlyException (403) if they are not an
   *     owner
   */
  @PostMapping("/{id}/rename")
  String rename(
      @AuthenticationPrincipal SignedInUser user,
      @PathVariable long id,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      households.rename(id, user.userId(), name);
      return Htmx.redirect(request, response, "/households");
    } catch (InvalidHouseholdNameException e) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      fill(model, user, request, "", null, name, NAME_INVALID);
    } catch (OptimisticLockingFailureException e) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      fill(model, user, request, "", null, name, CONFLICT);
    }
    return Htmx.view(request, response, "household/_rename", "household/page");
  }

  private void fill(
      Model model,
      SignedInUser user,
      HttpServletRequest request,
      String createName,
      String createError,
      String renameName,
      String renameError) {
    CurrentHousehold current = resolver.resolve(request).orElse(null);
    String shownRename = renameName != null ? renameName : current == null ? "" : current.name();
    model.addAttribute(
        "page",
        new HouseholdsPage(
            households.listFor(user.userId()),
            current,
            createName,
            createError,
            shownRename,
            renameError));
  }
}
