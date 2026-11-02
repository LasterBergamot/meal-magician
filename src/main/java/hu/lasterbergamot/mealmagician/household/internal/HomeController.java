package hu.lasterbergamot.mealmagician.household.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The address {@code /}: it shows nothing itself and sends a signed-in person to where they belong.
 *
 * <p>Someone with a household goes to the week plan, which is what they open the app for; someone
 * who belongs to no household yet goes to the households page, where they can create one. The
 * targets are plain paths on purpose: this module may not depend on {@code planning}, and a path
 * needs no import. The security configuration already sends an anonymous visitor to the sign-in
 * page before this method runs.
 */
@Controller
class HomeController {

  /** Where a person with a household lands: the week view. */
  static final String PLAN_PATH = "/plan";

  /** Where a person without a household lands: the page that creates one. */
  static final String HOUSEHOLDS_PATH = "/households";

  private final CurrentHouseholdResolver resolver;

  HomeController(CurrentHouseholdResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * Redirects to the first page that is useful to this person.
   *
   * @param request the current request, whose session holds the chosen household
   * @return a redirect to the plan when the person has a current household, otherwise to the
   *     households page
   */
  @GetMapping("/")
  String home(HttpServletRequest request) {
    return "redirect:" + (resolver.resolve(request).isPresent() ? PLAN_PATH : HOUSEHOLDS_PATH);
  }
}
