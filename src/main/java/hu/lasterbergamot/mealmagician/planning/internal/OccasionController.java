package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.OccasionConflictException;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
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
 * The Occasions pages: the list, the create and edit form, the detail page and delete (issue #112).
 * The menu's actions and the templates live in {@link OccasionMenuController} and {@link
 * OccasionTemplateController} (issue #113).
 *
 * <p>Every member may use every route (PLAN.md D26). Each one takes the {@link CurrentHousehold},
 * so the service looks up occasions in that household only, and another household's occasion is a
 * 404 on every route. A rejected form answers 422 with the same form and the typed values. A stale
 * edit or delete answers 409. A saved form ends with {@link Htmx#redirect}.
 */
@Controller
@RequestMapping("/occasions")
class OccasionController {

  private static final String FRAGMENT = "occasion/_form";
  private static final String PAGE = "occasion/form";

  private final OccasionService occasions;
  private final OccasionPageFactory pages;

  OccasionController(OccasionService occasions, OccasionPageFactory pages) {
    this.occasions = occasions;
    this.pages = pages;
  }

  /**
   * Shows the Occasions list: the next occasion as a card, then the later ones, then the past ones,
   * or an empty state.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param model receives the list model
   * @return the list page
   */
  @GetMapping
  String list(@AuthenticationPrincipal SignedInUser user, CurrentHousehold household, Model model) {
    model.addAttribute("page", pages.list(user.userId(), household.id()));
    return "occasion/list";
  }

  /**
   * Shows the empty form for a new occasion.
   *
   * @param household the household of the request
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the form model
   * @return the form page, or only the form for htmx
   */
  @GetMapping("/new")
  String blank(
      CurrentHousehold household,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    model.addAttribute("page", OccasionFormPage.of(OccasionForm.blank(), pages.typeOptions()));
    return Htmx.view(request, response, FRAGMENT, PAGE);
  }

  /**
   * Adds an occasion with an empty menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param name the typed name
   * @param date the typed date, as {@code YYYY-MM-DD}
   * @param type the chosen kind, by enum name
   * @param notes the typed notes
   * @param request the current request
   * @param response the response
   * @param model receives the form model when a field is rejected
   * @return a redirect to the new occasion's page, or the form with {@code 422} and the message
   *     under the field
   */
  @PostMapping
  String create(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String date,
      @RequestParam(defaultValue = "") String type,
      @RequestParam(defaultValue = "") String notes,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    OccasionForm form = new OccasionForm(null, 0, name, date, type, notes);
    try {
      OccasionView created = occasions.create(user.userId(), household.id(), form.details());
      return Htmx.redirect(request, response, "/occasions/" + created.id());
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute("page", OccasionFormPage.rejected(form, pages.typeOptions(), refused));
      return Htmx.view(request, response, FRAGMENT, PAGE);
    }
  }

  /**
   * Shows one occasion with its menu.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion to show
   * @param model receives the detail model
   * @return the detail page
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  @GetMapping("/{occasionId}")
  String detail(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      Model model) {
    model.addAttribute("page", pages.detail(user.userId(), household.id(), occasionId));
    return "occasion/detail";
  }

  /**
   * Shows the form filled with a stored occasion.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion to edit
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the form model
   * @return the form page, or only the form for htmx
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  @GetMapping("/{occasionId}/edit")
  String edit(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    OccasionForm form = OccasionForm.of(occasions.find(user.userId(), household.id(), occasionId));
    model.addAttribute("page", OccasionFormPage.of(form, pages.typeOptions()));
    return Htmx.view(request, response, FRAGMENT, PAGE);
  }

  /**
   * Saves the changes to an occasion. The menu is not touched.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion to change
   * @param version the version the form was opened with
   * @param name the typed name
   * @param date the typed date, as {@code YYYY-MM-DD}
   * @param type the chosen kind, by enum name
   * @param notes the typed notes
   * @param request the current request
   * @param response the response
   * @param model receives the form model when the change is refused
   * @return a redirect to the occasion's page, or the form with {@code 422} (a field is wrong) or
   *     {@code 409} (someone else saved first)
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household, whatever was typed
   */
  @PostMapping("/{occasionId}")
  String update(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(defaultValue = "-1") long version,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String date,
      @RequestParam(defaultValue = "") String type,
      @RequestParam(defaultValue = "") String notes,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    OccasionForm form = new OccasionForm(occasionId, version, name, date, type, notes);
    try {
      occasions.edit(user.userId(), household.id(), occasionId, version, form.details());
      return Htmx.redirect(request, response, "/occasions/" + occasionId);
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute("page", OccasionFormPage.rejected(form, pages.typeOptions(), refused));
    } catch (OccasionConflictException stale) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      model.addAttribute("page", OccasionFormPage.stale(form, pages.typeOptions()));
    }
    return Htmx.view(request, response, FRAGMENT, PAGE);
  }

  /**
   * Deletes an occasion and its menu. A stale delete is refused and nothing is removed.
   *
   * <p>With htmx the 409 is a banner in {@code #occasion-conflict} (HX-Retarget), so the detail
   * page stays and the dialog closes. Without htmx the whole detail page is drawn again with the
   * same banner.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param occasionId the occasion to delete
   * @param version the version the person last saw, sent in the query string
   * @param request the current request
   * @param response the response
   * @param model receives the banner's reload link, or the detail page for a plain request
   * @return a redirect to the list, the banner fragment for htmx, or the detail page, which answer
   *     {@code 409} when the delete was stale
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException (404) if the occasion
   *     does not exist in this household
   */
  @PostMapping("/{occasionId}/delete")
  String delete(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long occasionId,
      @RequestParam(defaultValue = "-1") long version,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      occasions.delete(user.userId(), household.id(), occasionId, version);
      return Htmx.redirect(request, response, "/occasions");
    } catch (OccasionConflictException stale) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      if (Htmx.isRequest(request)) {
        response.setHeader("HX-Retarget", "#occasion-conflict");
        response.setHeader("HX-Reswap", "innerHTML");
        model.addAttribute("reloadHref", "/occasions/" + occasionId);
        return "occasion/_conflict";
      }
      model.addAttribute(
          "page", pages.detail(user.userId(), household.id(), occasionId).withConflict());
      return "occasion/detail";
    }
  }
}
