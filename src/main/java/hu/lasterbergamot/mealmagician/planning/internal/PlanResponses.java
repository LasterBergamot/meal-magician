package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import hu.lasterbergamot.mealmagician.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * The answers every {@code /plan} controller shares: where a successful edit lands, and the two
 * banners that land in the week page's {@code #plan-conflict} region (409 for a stale edit, 422 for
 * a refused copy). They live here, and not in one controller, so the slot editor and the reuse
 * routes answer in exactly the same way.
 */
@Component
class PlanResponses {

  private final WeekPageFactory weeks;

  PlanResponses(WeekPageFactory weeks) {
    this.weeks = weeks;
  }

  /**
   * Ends a successful edit: the week page that holds the day, scrolled to the day's section.
   *
   * <p>A browser treats a navigation to the address it is already on, apart from the {@code #day}
   * part, as a jump within the page and does not reload it, so the edit would not show. When htmx
   * says (in {@code HX-Current-URL}) that the person is on that very week page, the answer also
   * carries {@code HX-Refresh}, which makes htmx reload the page right after moving to the day.
   *
   * @param day the day to land on
   * @param request the current request
   * @param response the response, which receives the redirect headers
   * @return {@code null}, because the redirect is the whole answer
   */
  String toDay(LocalDate day, HttpServletRequest request, HttpServletResponse response) {
    String url = WeekPage.hrefOf(IsoWeek.containing(day)) + "#" + DayPlan.idOf(day);
    Htmx.redirect(request, response, url);
    if (Htmx.isRequest(request) && isSamePage(request.getHeader("HX-Current-URL"), url)) {
      response.setHeader("HX-Refresh", "true");
    }
    return null;
  }

  /**
   * Tells whether the browser's current address is the target's path and query, fragment aside.
   *
   * @param currentUrl the {@code HX-Current-URL} header, possibly {@code null} or not an address
   * @param target the address to go to, with or without a {@code #fragment}
   * @return {@code true} if going to {@code target} would only jump within the current page
   */
  static boolean isSamePage(String currentUrl, String target) {
    if (currentUrl == null) {
      return false;
    }
    try {
      URI current = URI.create(currentUrl);
      String here =
          current.getRawQuery() == null
              ? current.getRawPath()
              : current.getRawPath() + "?" + current.getRawQuery();
      int hash = target.indexOf('#');
      String path = hash < 0 ? target : target.substring(0, hash);
      return here.equals(path);
    } catch (IllegalArgumentException notAnAddress) {
      return false;
    }
  }

  /**
   * Answers a stale edit with 409. htmx gets the warning alert, retargeted into {@code
   * #plan-conflict} (which {@code app.js} also uses to close open dialogs) with a Reload link; a
   * plain request gets the whole week page with the banner on top. Nothing was written.
   *
   * @param userId the signed-in person's id
   * @param householdId the household of the request
   * @param day a day of the week that changed
   * @param request the current request
   * @param response the response, which gets status 409
   * @param model receives the page or the Reload address
   * @return the view name to render
   */
  String conflict(
      long userId,
      long householdId,
      LocalDate day,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    response.setStatus(HttpServletResponse.SC_CONFLICT);
    IsoWeek week = IsoWeek.containing(day);
    if (Htmx.isRequest(request)) {
      response.setHeader("HX-Retarget", "#plan-conflict");
      response.setHeader("HX-Reswap", "innerHTML");
      model.addAttribute("reloadHref", WeekPage.hrefOf(week));
      return "plan/_conflict";
    }
    model.addAttribute("page", weeks.page(userId, householdId, week).withConflict());
    return "plan/week";
  }

  /**
   * Answers a refused copy (same target, or nothing to copy) with 422, in the same place as {@link
   * #conflict}: htmx gets a warning alert retargeted into {@code #plan-conflict}, so every open
   * dialog closes and the person sees why; a plain request gets the whole week page with the
   * message on top.
   *
   * @param userId the signed-in person's id
   * @param householdId the household of the request
   * @param day a day of the week the person was on
   * @param problem what the planner refused
   * @param request the current request
   * @param response the response, which gets status 422
   * @param model receives the page, or the message key and the Reload address
   * @return the view name to render
   */
  String reuseError(
      long userId,
      long householdId,
      LocalDate day,
      PlanProblem problem,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
    IsoWeek week = IsoWeek.containing(day);
    String key = reuseErrorKey(problem);
    if (Htmx.isRequest(request)) {
      response.setHeader("HX-Retarget", "#plan-conflict");
      response.setHeader("HX-Reswap", "innerHTML");
      model.addAttribute("messageKey", key);
      model.addAttribute("reloadHref", WeekPage.hrefOf(week));
      return "plan/_reuse_error";
    }
    model.addAttribute("page", weeks.page(userId, householdId, week).withReuseError(key));
    return "plan/week";
  }

  /**
   * The message key that explains a refused copy.
   *
   * @param problem what the planner refused
   * @return {@code plan.reuse.error.same} for {@code SAME_TARGET}, otherwise {@code
   *     plan.reuse.nothing}
   */
  static String reuseErrorKey(PlanProblem problem) {
    return problem == PlanProblem.SAME_TARGET ? "plan.reuse.error.same" : "plan.reuse.nothing";
  }
}
