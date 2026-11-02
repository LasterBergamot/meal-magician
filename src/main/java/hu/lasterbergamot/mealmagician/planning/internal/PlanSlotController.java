package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.InvalidMealException;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.PlanConflictException;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
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
 * The routes that edit the week plan: the pick sheet (add a library meal or a new one), the slot
 * editor, and the note, move, reorder and remove actions. Every success ends with {@link
 * Htmx#redirect} to the week page at the affected day, so the page the person sees is always the
 * stored plan.
 *
 * <p>Rules shared by all routes:
 *
 * <ul>
 *   <li>Every method takes {@link CurrentHousehold} first, so a person in no household gets 404
 *       before anything else.
 *   <li>Routes on an existing slot read it (household-scoped) <em>before</em> parsing any other
 *       parameter. A foreign slot is therefore 404 whatever else was sent, and the tenant guard,
 *       which sends no parameters, sees the home owner get 400 or 409 and never a 5xx.
 *   <li>A malformed date, slot type or id is 400 ({@link PlanParams}).
 *   <li>A stale version is 409 ({@link PlanResponses#conflict}): htmx retargets the answer into the
 *       page's {@code #plan-conflict} banner, a plain request gets the whole week page with the
 *       banner. The version defaults to {@code -1}, so a missing one is a conflict and never a
 *       silent write.
 * </ul>
 */
@Controller
@RequestMapping("/plan")
class PlanSlotController {

  private final PlanningService planning;
  private final PickPageFactory picks;
  private final SlotEditorFactory editors;
  private final PlanResponses responses;

  PlanSlotController(
      PlanningService planning,
      PickPageFactory picks,
      SlotEditorFactory editors,
      PlanResponses responses) {
    this.planning = planning;
    this.picks = picks;
    this.editors = editors;
    this.responses = responses;
  }

  /**
   * Shows the pick sheet's content for one day and slot type.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param date the day, {@code YYYY-MM-DD}
   * @param slot the slot type name, for example {@code LUNCH}
   * @param q the search text; empty lists the library
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link PickPage}
   * @return the sheet's content for htmx, otherwise a whole page
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code date} or {@code
   *     slot} is missing or malformed
   */
  @GetMapping("/pick")
  String pick(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String slot,
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    LocalDate day = PlanParams.date(date);
    SlotType type = PlanParams.slotType(slot);
    model.addAttribute(
        "page", picks.page(user.userId(), household.id(), day, type, q, PickQuick.empty()));
    return Htmx.view(request, response, "plan/_pick", "plan/pick");
  }

  /**
   * Plans a library meal on a day.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param date the day, {@code YYYY-MM-DD}
   * @param slot the slot type name
   * @param mealId the library meal's id
   * @param request the current request
   * @param response the response
   * @return a redirect to the week page at that day
   * @throws org.springframework.web.server.ResponseStatusException (400) if a parameter is missing
   *     or malformed
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException (404) if the meal is not
   *     in this household
   */
  @PostMapping("/slots")
  String add(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String slot,
      @RequestParam(required = false) String mealId,
      HttpServletRequest request,
      HttpServletResponse response) {
    LocalDate day = PlanParams.date(date);
    SlotType type = PlanParams.slotType(slot);
    long meal = PlanParams.id(mealId);
    planning.add(user.userId(), household.id(), day, type, meal, null);
    return responses.toDay(day, request, response);
  }

  /**
   * Creates a meal from its name and plans it on a day.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param date the day, {@code YYYY-MM-DD}
   * @param slot the slot type name
   * @param name the new meal's name
   * @param request the current request
   * @param response the response
   * @param model receives the page when the name is rejected
   * @return a redirect to the week page at that day; for a rejected name, with 422, the "Create and
   *     add" form for htmx or the whole pick page otherwise, with the typed name kept
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code date} or {@code
   *     slot} is missing or malformed
   */
  @PostMapping("/slots/quick")
  String quickAdd(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String slot,
      @RequestParam(defaultValue = "") String name,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    LocalDate day = PlanParams.date(date);
    SlotType type = PlanParams.slotType(slot);
    try {
      planning.quickAddAndPlan(user.userId(), household.id(), day, type, name);
      return responses.toDay(day, request, response);
    } catch (InvalidMealException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      PickQuick rejected = new PickQuick(name, PickQuick.errorKey(refused.problem()));
      model.addAttribute(
          "page", picks.page(user.userId(), household.id(), day, type, "", rejected));
      return Htmx.view(request, response, "plan/_pick_quick", "plan/pick");
    }
  }

  /**
   * Shows the editor of one planned slot.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link SlotEditor}
   * @return the sheet's content for htmx, otherwise a whole page
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household
   */
  @GetMapping("/slots/{slotId}")
  String editor(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    model.addAttribute("editor", editors.editor(user.userId(), household.id(), slotId));
    return Htmx.view(request, response, "plan/_slot_editor", "plan/slot");
  }

  /**
   * Sets or clears the note of a slot.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param version the version the editor showed; {@code -1} when missing
   * @param note the new note; empty clears it
   * @param request the current request
   * @param response the response
   * @param model receives the editor when the note is rejected or a plain request conflicts
   * @return a redirect to the week page at the slot's day; 422 with the note form for a note that
   *     is too long; 409 for a stale version
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household
   */
  @PostMapping("/slots/{slotId}/note")
  String note(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      @RequestParam(defaultValue = "-1") long version,
      @RequestParam(defaultValue = "") String note,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    SlotView current = planning.slot(user.userId(), household.id(), slotId);
    try {
      planning.editNote(user.userId(), household.id(), slotId, version, note);
      return responses.toDay(current.date(), request, response);
    } catch (InvalidPlanException refused) {
      response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
      model.addAttribute(
          "editor",
          editors.editor(user.userId(), household.id(), slotId).rejectedNote(note, version));
      return Htmx.view(request, response, "plan/_note_form", "plan/slot");
    } catch (PlanConflictException stale) {
      return responses.conflict(
          user.userId(), household.id(), current.date(), request, response, model);
    }
  }

  /**
   * Moves a slot to another day and slot type.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param version the version the editor showed; {@code -1} when missing
   * @param date the new day, {@code YYYY-MM-DD}
   * @param slot the new slot type name
   * @param request the current request
   * @param response the response
   * @param model receives the page when a plain request conflicts
   * @return a redirect to the week page at the new day; 409 for a stale version
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household; this is checked before the parameters
   * @throws org.springframework.web.server.ResponseStatusException (400) if {@code date} or {@code
   *     slot} is missing or malformed
   */
  @PostMapping("/slots/{slotId}/move")
  String move(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      @RequestParam(defaultValue = "-1") long version,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String slot,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    SlotView current = planning.slot(user.userId(), household.id(), slotId);
    LocalDate day = PlanParams.date(date);
    SlotType type = PlanParams.slotType(slot);
    try {
      SlotView moved = planning.move(user.userId(), household.id(), slotId, version, day, type);
      return responses.toDay(moved.date(), request, response);
    } catch (PlanConflictException stale) {
      return responses.conflict(
          user.userId(), household.id(), current.date(), request, response, model);
    }
  }

  /**
   * Moves a slot to a new place among the meals of its day and slot type.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param version the version the editor showed; {@code -1} when missing
   * @param position the wanted place, counted from 0
   * @param request the current request
   * @param response the response
   * @param model receives the page when a plain request conflicts
   * @return a redirect to the week page at the slot's day; 409 for a stale version
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household
   */
  @PostMapping("/slots/{slotId}/reorder")
  String reorder(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      @RequestParam(defaultValue = "-1") long version,
      @RequestParam(defaultValue = "0") int position,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    SlotView current = planning.slot(user.userId(), household.id(), slotId);
    try {
      planning.reorder(user.userId(), household.id(), slotId, version, position);
      return responses.toDay(current.date(), request, response);
    } catch (PlanConflictException stale) {
      return responses.conflict(
          user.userId(), household.id(), current.date(), request, response, model);
    }
  }

  /**
   * Takes a slot out of the plan. The confirm dialog posts here with the version in the query
   * string.
   *
   * @param user the signed-in person
   * @param household the household of the request
   * @param slotId the slot
   * @param version the version the editor showed; {@code -1} when missing
   * @param request the current request
   * @param response the response
   * @param model receives the page when a plain request conflicts
   * @return a redirect to the week page at the slot's day; 409 for a stale version
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException (404) if the slot is not
   *     in this household
   */
  @PostMapping("/slots/{slotId}/remove")
  String remove(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @PathVariable long slotId,
      @RequestParam(defaultValue = "-1") long version,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    SlotView current = planning.slot(user.userId(), household.id(), slotId);
    try {
      planning.remove(user.userId(), household.id(), slotId, version);
      return responses.toDay(current.date(), request, response);
    } catch (PlanConflictException stale) {
      return responses.conflict(
          user.userId(), household.id(), current.date(), request, response, model);
    }
  }
}
