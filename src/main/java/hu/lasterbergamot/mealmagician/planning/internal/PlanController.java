package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The week planner page. Editing comes later; today it only shows one ISO week of the household's
 * plan.
 *
 * <p>The method takes a {@link CurrentHousehold}, the verified household of the request, so a
 * person in no household gets a 404 before anything is read (PLAN.md D8). Every member may look:
 * nothing here is owner-only.
 */
@Controller
@RequestMapping("/plan")
class PlanController {

  private final WeekPageFactory weeks;

  PlanController(WeekPageFactory weeks) {
    this.weeks = weeks;
  }

  /**
   * Shows one week of the plan.
   *
   * @param user the signed-in person
   * @param household the household of the request; declared before the request parameters so that a
   *     person in no household gets 404 first
   * @param week the wanted week as {@code YYYY-Www}; missing or not a real week means the current
   *     week
   * @param request the current request
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link WeekPage}
   * @return the whole page, or only the week for htmx (the stepper swaps just that region)
   */
  @GetMapping
  String week(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    IsoWeek shown = IsoWeek.parse(week).orElseGet(weeks::currentWeek);
    model.addAttribute("page", weeks.page(user.userId(), household.id(), shown));
    return Htmx.view(request, response, "plan/_week", "plan/week");
  }
}
