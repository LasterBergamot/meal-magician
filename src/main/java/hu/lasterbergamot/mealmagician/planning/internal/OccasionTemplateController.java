package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.OccasionTemplateService;
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
 * The occasion template pages (issue #113): a template's page, "Plan it again" on a date, and
 * delete. Every member may use every route (PLAN.md D26). Every method takes {@link
 * CurrentHousehold} first, and the template is read (household-scoped) before the date is parsed,
 * so another household's template is a 404 whatever else was sent.
 */
@Controller
@RequestMapping("/occasions/templates/{templateId}")
class OccasionTemplateController {

  private final OccasionTemplateService templates;
  private final OccasionPageFactory pages;

  OccasionTemplateController(OccasionTemplateService templates, OccasionPageFactory pages) {
    this.templates = templates;
    this.pages = pages;
  }

  /**
   * Shows one template: its kind, notes and menu, the "Plan it again" form and Delete.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template to show
   * @param model receives the page
   * @return the template page
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     does not exist in this household
   */
  @GetMapping
  String show(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      Model model) {
    model.addAttribute("page", pages.template(user.userId(), household.id(), templateId));
    return "occasion/template";
  }

  /**
   * Plans the template again: a new occasion with the template's name, kind, notes and menu on the
   * given date.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template to use
   * @param date the typed date, as {@code YYYY-MM-DD}
   * @param request the current request
   * @param response the response
   * @param model receives the page when the date is refused
   * @return a redirect to the new occasion's page; for a missing or malformed date, the "Plan it
   *     again" form with 422 and the message
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     does not exist in this household; this is checked before the date
   */
  @PostMapping("/use")
  String use(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      @RequestParam(defaultValue = "") String date,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    templates.find(user.userId(), household.id(), templateId);
    try {
      OccasionView created =
          templates.use(user.userId(), household.id(), templateId, OccasionForm.parseDate(date));
      return Htmx.redirect(request, response, "/occasions/" + created.id());
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "page",
          pages
              .template(user.userId(), household.id(), templateId)
              .withUse(date, "occasion.error.date"));
      return Htmx.view(request, response, "occasion/_use_form", "occasion/template");
    }
  }

  /**
   * Deletes the template. The occasions already made from it stay.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template to delete
   * @param request the current request
   * @param response the response
   * @return a redirect to the Occasions list
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     does not exist in this household
   */
  @PostMapping("/delete")
  String delete(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      HttpServletRequest request,
      HttpServletResponse response) {
    templates.delete(user.userId(), household.id(), templateId);
    return Htmx.redirect(request, response, "/occasions");
  }
}
