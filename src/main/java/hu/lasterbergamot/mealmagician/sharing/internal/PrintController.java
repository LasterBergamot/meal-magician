package hu.lasterbergamot.mealmagician.sharing.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import java.time.Clock;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The print page of a week, {@code GET /plan/print} (PLAN.md D29): a light page without the app
 * shell, built from the same days template as the public share page, plus the slot notes. Only
 * members of the household can open it, and every member may.
 */
@Controller
class PrintController {

  private final PrintPageFactory pages;
  private final Clock clock;

  PrintController(PrintPageFactory pages, Clock clock) {
    this.pages = pages;
    this.clock = clock;
  }

  /**
   * Shows one week ready to print.
   *
   * @param user the signed-in person
   * @param household the household of the request; declared first so that a person in no household
   *     gets 404 before anything is read
   * @param week the week code, for example {@code 2026-W45}; missing or malformed means the current
   *     week, as on the plan page
   * @param model receives the {@link PrintPage}
   * @return the print view; it is always a whole page, because the browser opens it as a navigation
   *     and not as an htmx fragment
   */
  @GetMapping("/plan/print")
  String print(
      @AuthenticationPrincipal SignedInUser user,
      CurrentHousehold household,
      @RequestParam(required = false) String week,
      Model model) {
    model.addAttribute(
        "page", pages.page(user.userId(), household.id(), ShareForm.mondayOf(week, clock)));
    return "sharing/print";
  }
}
