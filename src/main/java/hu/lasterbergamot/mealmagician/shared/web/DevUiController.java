package hu.lasterbergamot.mealmagician.shared.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The living style guide at {@code /dev/ui}: every icon, button and (later) component on one page,
 * for designers, developers and the browser tests (plan-ui-redesign.md R2a).
 *
 * <p>Only the {@code dev} profile creates this controller, so production has no such page: once a
 * person is signed in the URL answers 404. It is deliberately not in {@code
 * SecurityConfig.PUBLIC_PATHS}; the endpoint-auth walk checks that anonymous visitors are sent to
 * the login page.
 */
@Controller
@Profile("dev")
public class DevUiController {

  /**
   * Shows the style guide.
   *
   * @return the view {@code dev/ui}, which includes one section fragment per component family
   */
  @GetMapping("/dev/ui")
  public String styleGuide() {
    return "dev/ui";
  }
}
