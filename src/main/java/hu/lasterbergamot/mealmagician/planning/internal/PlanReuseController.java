package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.PlanConflictException;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateService;
import hu.lasterbergamot.mealmagician.planning.PlanTemplateView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The routes that reuse the plan from the week page: the reuse sheet, copying a day or a week,
 * saving one as a template and applying a template.
 *
 * <p>Rules shared by all routes:
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold} first, so a person in no household gets 404
 *       before anything else.
 *   <li>{@code apply} reads the template (household-scoped) <em>before</em> parsing any other
 *       parameter, so another household's template is 404 whatever else was sent.
 *   <li>A missing or malformed mode, scope, date or week code is 400 ({@link PlanParams}).
 *   <li>A refused copy (same target, nothing to copy) is 422 and a lost race on a replace is 409;
 *       both land in the week page's {@code #plan-conflict} banner ({@link PlanResponses}).
 * </ul>
 */
@Controller
@RequestMapping("/plan")
class PlanReuseController {

  private final PlanningService planning;
  private final PlanTemplateService templates;
  private final ReusePageFactory reuses;
  private final PlanResponses responses;

  PlanReuseController(
      PlanningService planning,
      PlanTemplateService templates,
      ReusePageFactory reuses,
      PlanResponses responses) {
    this.planning = planning;
    this.templates = templates;
    this.reuses = reuses;
    this.responses = responses;
  }

  /**
   * Shows the reuse sheet's content for one day.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param date the day, {@code YYYY-MM-DD}
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link ReusePage}
   * @return the sheet's content for htmx, otherwise a whole page
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code date} is missing
   *     or malformed
   */
  @GetMapping("/reuse/day")
  String day(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String date,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    LocalDate day = PlanParams.date(date);
    model.addAttribute("page", reuses.day(user.userId(), household.id(), day, ReuseSave.empty()));
    return Htmx.view(request, response, "plan/_reuse", "plan/reuse");
  }

  /**
   * Shows the reuse sheet's content for one week.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param week the week code, for example {@code 2026-W45}
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link ReusePage}
   * @return the sheet's content for htmx, otherwise a whole page
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code week} is missing
   *     or malformed
   */
  @GetMapping("/reuse/week")
  String week(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    IsoWeek target = PlanParams.week(week);
    model.addAttribute(
        "page", reuses.week(user.userId(), household.id(), target, ReuseSave.empty()));
    return Htmx.view(request, response, "plan/_reuse", "plan/reuse");
  }

  /**
   * Copies one day onto another.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mode {@code MERGE} or {@code REPLACE}
   * @param from the day to copy, {@code YYYY-MM-DD}
   * @param to the day to copy onto, {@code YYYY-MM-DD}
   * @param request the current request
   * @param response the response
   * @param model receives the page or the message when the copy is refused
   * @return a redirect to the target day; 422 if both days are the same or the source is empty; 409
   *     if a replace lost a race
   * @throws org.springframework.web.server.ResponseStatusException (400) if a parameter is missing
   *     or malformed
   */
  @PostMapping("/days/copy")
  String copyDay(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    CopyMode copyMode = PlanParams.mode(mode);
    LocalDate source = PlanParams.date(from);
    LocalDate target = PlanParams.date(to);
    try {
      planning.copyDay(user.userId(), household.id(), source, target, copyMode);
      return responses.toDay(target, request, response);
    } catch (InvalidPlanException refused) {
      return responses.reuseError(
          user.userId(), household.id(), source, refused.problem(), request, response, model);
    } catch (PlanConflictException stale) {
      return responses.conflict(user.userId(), household.id(), source, request, response, model);
    }
  }

  /**
   * Copies one week onto another, each meal keeping its weekday.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param mode {@code MERGE} or {@code REPLACE}
   * @param from the week to copy, for example {@code 2026-W45}
   * @param to the week to copy onto
   * @param request the current request
   * @param response the response
   * @param model receives the page or the message when the copy is refused
   * @return a redirect to the target week's Monday; 422 if both weeks are the same or the source is
   *     empty; 409 if a replace lost a race
   * @throws org.springframework.web.server.ResponseStatusException (400) if a parameter is missing
   *     or malformed
   */
  @PostMapping("/weeks/copy")
  String copyWeek(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    CopyMode copyMode = PlanParams.mode(mode);
    IsoWeek source = PlanParams.week(from);
    IsoWeek target = PlanParams.week(to);
    try {
      planning.copyWeek(user.userId(), household.id(), source.monday(), target.monday(), copyMode);
      return responses.toDay(target.monday(), request, response);
    } catch (InvalidPlanException refused) {
      return responses.reuseError(
          user.userId(),
          household.id(),
          source.monday(),
          refused.problem(),
          request,
          response,
          model);
    } catch (PlanConflictException stale) {
      return responses.conflict(
          user.userId(), household.id(), source.monday(), request, response, model);
    }
  }

  /**
   * Saves a day or a week of the plan as a template.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param scope {@code DAY} or {@code WEEK}
   * @param date the day, or any day of the week, {@code YYYY-MM-DD}
   * @param name the template's name
   * @param request the current request
   * @param response the response
   * @param model receives the success text or the form with its error
   * @return for htmx the success alert, otherwise a redirect to the templates page; for a refused
   *     save, with 422, the save form (htmx) or the whole reuse page, with the typed name kept
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code scope} or {@code
   *     date} is missing or malformed
   */
  @PostMapping("/templates")
  String save(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String date,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    TemplateScope templateScope = PlanParams.scope(scope);
    LocalDate day = PlanParams.date(date);
    try {
      PlanTemplateView saved =
          templates.save(user.userId(), household.id(), templateScope, day, name);
      if (Htmx.isRequest(request)) {
        model.addAttribute("savedName", saved.name());
        return "plan/_template_saved";
      }
      Htmx.redirect(request, response, "/plan/templates");
      return null;
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      ReuseSave rejected = new ReuseSave(name, ReuseSave.errorKey(refused.problem()));
      model.addAttribute(
          "page",
          templateScope == TemplateScope.DAY
              ? reuses.day(user.userId(), household.id(), day, rejected)
              : reuses.week(user.userId(), household.id(), IsoWeek.containing(day), rejected));
      return Htmx.view(request, response, "plan/_reuse_save", "plan/reuse");
    }
  }

  /**
   * Applies a template to a day or a week.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param templateId the template
   * @param mode {@code MERGE} or {@code REPLACE}
   * @param date the day to fill, or any day of the week to fill, {@code YYYY-MM-DD}
   * @param request the current request
   * @param response the response
   * @param model receives the page when a plain request conflicts
   * @return a redirect to the filled day (a day template) or to the Monday of the filled week (a
   *     week template); 409 if a replace lost a race
   * @throws hu.lasterbergamot.mealmagician.planning.TemplateNotFoundException (404) if the template
   *     is not in this household; this is checked before the parameters
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code mode} or {@code
   *     date} is missing or malformed
   */
  @PostMapping("/templates/{templateId}/apply")
  String apply(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long templateId,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String date,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    PlanTemplateView template = templates.find(user.userId(), household.id(), templateId);
    CopyMode copyMode = PlanParams.mode(mode);
    LocalDate day = PlanParams.date(date);
    LocalDate landing =
        template.scope() == TemplateScope.WEEK ? IsoWeek.containing(day).monday() : day;
    try {
      templates.apply(user.userId(), household.id(), templateId, day, copyMode);
      return responses.toDay(landing, request, response);
    } catch (PlanConflictException stale) {
      return responses.conflict(user.userId(), household.id(), day, request, response, model);
    }
  }
}
