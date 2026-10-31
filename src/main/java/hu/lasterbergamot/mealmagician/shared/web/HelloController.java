package hu.lasterbergamot.mealmagician.shared.web;

import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The temporary home page: proves that layout, translations, Tailwind, htmx and Alpine work
 * together (phase 0 goal, PLAN.md section 3). It moves into the {@code household} module when the
 * real landing page (current week or household chooser) arrives in phase 1.
 *
 * <p>The URL serves both representations: a normal request gets the whole page, an htmx request
 * (the "refresh" button) only the greeting fragment. Every later screen follows this pattern.
 */
@Controller
public class HelloController {

  private final Clock clock;
  private final Messages messages;

  /**
   * Creates the controller.
   *
   * @param clock the application clock (Europe/Budapest)
   * @param messages the translation helper, used here only for the date's language
   */
  public HelloController(Clock clock, Messages messages) {
    this.clock = clock;
    this.messages = messages;
  }

  /**
   * Shows the greeting.
   *
   * @param request the current request, to tell htmx from a normal navigation
   * @param response the response, which receives {@code Vary: HX-Request}
   * @param model receives the {@link HelloView}
   * @return the fragment view for htmx requests, otherwise the full page view
   */
  @GetMapping("/")
  public String hello(HttpServletRequest request, HttpServletResponse response, Model model) {
    String today =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(messages.locale())
            .format(LocalDate.now(clock));
    model.addAttribute("hello", new HelloView(today));
    return Htmx.view(request, response, "hello/_greeting", "hello/page");
  }
}
