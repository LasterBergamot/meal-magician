package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateService;
import hu.lasterbergamot.mealmagician.planning.TemplateConflictException;
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
 * The plan template pages: the list of day and week templates, one template's page, and its rename
 * and delete actions. Templates are created on the plan (a day or a week is saved there), so this
 * controller never creates one.
 *
 * <p>Rules shared by all routes:
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold} first, so a person in no household gets 404
 *       before anything else.
 *   <li>A template of another household is 404 (the service looks it up in this household only).
 *   <li>Rename and delete send the version the page showed. The version defaults to {@code -1}, so
 *       a missing one is a conflict (409) and never a silent write.
 *   <li>Every member may rename and delete (PLAN.md D26); nothing here is owner-only.
 * </ul>
 */
@Controller
@RequestMapping("/plan/templates")
class PlanTemplateController {

  /** Where the page goes once a template is gone; the list shows what is left. */
  private static final String LIST_PATH = "/plan/templates";

  private final PlanTemplateService templates;
  private final TemplatePageFactory pages;

  /**
   * Creates the controller.
   *
   * @param templates the plan template service, which checks the household and the version
   * @param pages builds the page models
   */
  PlanTemplateController(PlanTemplateService templates, TemplatePageFactory pages) {
    this.templates = templates;
    this.pages = pages;
  }

  /**
   * Shows the household's templates in two groups: days and weeks.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param model receives the {@link TemplatesPage}
   * @return the list page
   */
  @GetMapping
  String list(@AuthenticationPrincipal SignedInUser user, CurrentHousehold household, Model model) {
    model.addAttribute("page", pages.list(user.userId(), household.id()));
    return "plan/templates";
  }

  /**
   * Shows one template with its meals, and the rename and delete controls.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template
   * @param model receives the {@link TemplatePage}
   * @return the template page
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     is not in this household
   */
  @GetMapping("/{templateId}")
  String detail(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      Model model) {
    model.addAttribute("page", pages.detail(user.userId(), household.id(), templateId));
    return "plan/template";
  }

  /**
   * Gives a template a new name. A success sends the browser back to the template's page. A blank
   * or too-long name is refused with 422 and the rename card again, with the typed name kept. A
   * stale version is refused with 409 and the card with the warning.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template
   * @param version the version the page showed; {@code -1} when missing
   * @param name the wanted name
   * @param request the current request
   * @param response the response
   * @param model receives the page when the rename is refused
   * @return a redirect to the template's page, or the page or card with the error
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     is not in this household; this is checked before the version
   */
  @PostMapping("/{templateId}/rename")
  String rename(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      @RequestParam(defaultValue = "-1") long version,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      templates.rename(user.userId(), household.id(), templateId, version, name);
      return Htmx.redirect(request, response, detailPath(templateId));
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "page",
          pages
              .detail(user.userId(), household.id(), templateId)
              .withRename(name, TemplatePage.renameErrorKey(refused.problem())));
    } catch (TemplateConflictException stale) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      model.addAttribute(
          "page",
          pages
              .detail(user.userId(), household.id(), templateId)
              .withRename(name, TemplatePage.CONFLICT_KEY));
    }
    return Htmx.view(request, response, "plan/_template_rename", "plan/template");
  }

  /**
   * Deletes a template. Plan days and weeks that were filled from it are not touched, because
   * applying a template copies its meals into the plan. A success sends the browser to the list. A
   * stale version gets 409: for htmx the banner goes into {@code #plan-conflict} and the dialog
   * closes; a plain request gets the whole page with the banner.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template
   * @param version the version the page showed; {@code -1} when missing
   * @param request the current request
   * @param response the response
   * @param model receives the page or the reload link when the version is stale
   * @return a redirect to the list, or the banner or page with 409
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     is not in this household; this is checked before the version
   */
  @PostMapping("/{templateId}/delete")
  String delete(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      @RequestParam(defaultValue = "-1") long version,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      templates.delete(user.userId(), household.id(), templateId, version);
      return Htmx.redirect(request, response, LIST_PATH);
    } catch (TemplateConflictException stale) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      if (Htmx.isRequest(request)) {
        response.setHeader("HX-Retarget", "#plan-conflict");
        response.setHeader("HX-Reswap", "innerHTML");
        model.addAttribute("reloadHref", detailPath(templateId));
        return "plan/_template_conflict";
      }
      model.addAttribute(
          "page", pages.detail(user.userId(), household.id(), templateId).withConflict());
      return "plan/template";
    }
  }

  /** The address of one template's page. */
  private static String detailPath(long templateId) {
    return LIST_PATH + "/" + templateId;
  }
}
