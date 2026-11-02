package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.OccasionTemplateService;
import hu.lasterbergamot.mealmagician.planning.OccasionTemplateView;
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
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The routes that edit an occasion's menu and save it as a template (issue #113): the pick sheet,
 * add a library meal or a new one, move a line, remove a line, and "Save as a template".
 *
 * <p>Rules shared by all routes:
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold} first, so a person in no household gets 404
 *       before anything else, and every lookup is scoped to that household: another household's
 *       occasion or menu item is a 404 (PLAN.md D8). Every member may use every route (D26).
 *   <li>The occasion is read <em>before</em> any other parameter is parsed. The tenant guard sends
 *       no parameters, so a stranger must meet the 404 first, and the home owner a 400 or 422.
 *   <li>A success redirects to the occasion's page <em>without</em> a {@code #} fragment, because a
 *       URL that differs only in its fragment would not reload the page. When htmx says the person
 *       is on that very page, the answer also carries {@code HX-Refresh}.
 * </ul>
 */
@Controller
@RequestMapping("/occasions/{occasionId}")
class OccasionMenuController {

  private final OccasionService occasions;
  private final OccasionTemplateService templates;
  private final OccasionPickPageFactory picks;
  private final OccasionPageFactory pages;

  OccasionMenuController(
      OccasionService occasions,
      OccasionTemplateService templates,
      OccasionPickPageFactory picks,
      OccasionPageFactory pages) {
    this.occasions = occasions;
    this.templates = templates;
    this.picks = picks;
    this.pages = pages;
  }

  /**
   * Shows the pick sheet's content: the library meals that can go on the menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param q the search text; empty lists the library
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link OccasionPickPage}
   * @return the sheet's content for htmx, otherwise a whole page
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  @GetMapping("/pick")
  String pick(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    model.addAttribute(
        "page", picks.page(user.userId(), household.id(), occasionId, q, PickQuick.empty()));
    return Htmx.view(request, response, "occasion/_pick", "occasion/pick");
  }

  /**
   * Adds a library meal to the end of the menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param mealId the library meal's id
   * @param request the current request
   * @param response the response
   * @return a redirect to the occasion's page
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code mealId} is
   *     missing or malformed
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal is not
   *     in this household
   */
  @PostMapping("/menu")
  String add(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(required = false) String mealId,
      HttpServletRequest request,
      HttpServletResponse response) {
    occasions.find(user.userId(), household.id(), occasionId);
    long meal = PlanParams.id(mealId);
    occasions.addItem(user.userId(), household.id(), occasionId, meal);
    return back(occasionId, request, response);
  }

  /**
   * Creates a meal from its name and adds it to the menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param name the new meal's name
   * @param request the current request
   * @param response the response
   * @param model receives the page when the name is rejected
   * @return a redirect to the occasion's page; for a rejected name, with 422, the "Create and add"
   *     form for htmx or the whole pick page otherwise, with the typed name kept
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  @PostMapping("/menu/quick")
  String quickAdd(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      occasions.quickAddItem(user.userId(), household.id(), occasionId, name);
      return back(occasionId, request, response);
    } catch (InvalidMealException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      PickQuick rejected = new PickQuick(name, PickQuick.errorKey(refused.problem()));
      model.addAttribute(
          "page", picks.page(user.userId(), household.id(), occasionId, "", rejected));
      return Htmx.view(request, response, "occasion/_pick_quick", "occasion/pick");
    }
  }

  /**
   * Takes a line off the menu. The later lines move up to close the gap.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param itemId the menu item to remove
   * @param request the current request
   * @param response the response
   * @return a redirect to the occasion's page
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   * @throws hu.lasterbergamot.mealmagician.planning.MenuItemNotFoundException (404) if the item is
   *     not on this occasion's menu
   */
  @PostMapping("/menu/{itemId}/remove")
  String remove(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @PathVariable long itemId,
      HttpServletRequest request,
      HttpServletResponse response) {
    occasions.removeItem(user.userId(), household.id(), occasionId, itemId);
    return back(occasionId, request, response);
  }

  /**
   * Moves a line to a new place on the menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param itemId the menu item to move
   * @param position the wanted place, counted from 0; the service clamps it into the menu
   * @param request the current request
   * @param response the response
   * @return a redirect to the occasion's page
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   * @throws hu.lasterbergamot.mealmagician.planning.MenuItemNotFoundException (404) if the item is
   *     not on this occasion's menu
   */
  @PostMapping("/menu/{itemId}/move")
  String move(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @PathVariable long itemId,
      @RequestParam(defaultValue = "0") int position,
      HttpServletRequest request,
      HttpServletResponse response) {
    occasions.moveItem(user.userId(), household.id(), occasionId, itemId, position);
    return back(occasionId, request, response);
  }

  /**
   * Saves the occasion's menu, kind and notes as a template.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion
   * @param name the template's name
   * @param request the current request
   * @param response the response
   * @param model receives the page when the save is refused
   * @return a redirect to the new template's page; for a blank or long name, or an empty menu, the
   *     save form with 422 and the message, the typed name kept
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  @PostMapping("/template")
  String saveAsTemplate(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      OccasionTemplateView saved =
          templates.saveFromOccasion(user.userId(), household.id(), occasionId, name);
      return Htmx.redirect(request, response, "/occasions/templates/" + saved.id());
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "page",
          pages
              .detail(user.userId(), household.id(), occasionId)
              .withSave(name, OccasionDetailPage.saveErrorKey(refused.problem())));
      return Htmx.view(request, response, "occasion/_save_template", "occasion/detail");
    }
  }

  /**
   * Ends a successful menu edit: the occasion's page, without a {@code #menu} fragment. A URL that
   * differs only in its fragment does not reload, so when htmx says the person is on that very page
   * the answer also carries {@code HX-Refresh}.
   */
  private static String back(
      long occasionId, HttpServletRequest request, HttpServletResponse response) {
    String url = "/occasions/" + occasionId;
    Htmx.redirect(request, response, url);
    if (Htmx.isRequest(request)
        && PlanResponses.isSamePage(request.getHeader("HX-Current-URL"), url)) {
      response.setHeader("HX-Refresh", "true");
    }
    return null;
  }
}
