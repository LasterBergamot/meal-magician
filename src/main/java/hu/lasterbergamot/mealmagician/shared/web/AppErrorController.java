package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renders our own 404, 403 and 500 pages. The servlet container forwards every unhandled error to
 * {@code /error}; declaring an {@link ErrorController} replaces Spring Boot's default, which would
 * show the "Whitelabel" page or JSON.
 *
 * <p>A normal request gets a full page. An htmx request gets a small fragment that {@code
 * HX-Retarget} routes into the layout's {@code #error-slot}, so a failed action shows a message
 * without wiping the part of the page it targeted. Status 401 and 403 are the exception: {@code
 * app.js} sends the whole page to the home page for those (an expired session looks like a 403), so
 * there is nothing to retarget.
 */
@Controller
public class AppErrorController implements ErrorController {

  /**
   * Describes the error and picks the page or the fragment.
   *
   * @param request the forwarded request; the container stores the original status in an attribute
   * @param response keeps the original status code, so browsers and htmx still see 404/500
   * @param model receives the {@link ErrorView}
   * @return the fragment view for htmx requests, otherwise the full page view
   */
  @RequestMapping("${server.error.path:/error}")
  public String error(HttpServletRequest request, HttpServletResponse response, Model model) {
    int status = statusOf(request);
    response.setStatus(status);
    model.addAttribute("error", describe(status));
    String view = Htmx.view(request, response, "error/_message", "error/page");
    if (Htmx.isRequest(request) && status != 401 && status != 403) {
      response.setHeader("HX-Retarget", "#error-slot");
      response.setHeader("HX-Reswap", "innerHTML");
    }
    return view;
  }

  private static int statusOf(HttpServletRequest request) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    return status instanceof Integer code ? code : 500;
  }

  private static ErrorView describe(int status) {
    return switch (status) {
      case 403 -> new ErrorView(status, "error.403.title", "error.403.message");
      case 404 -> new ErrorView(status, "error.404.title", "error.404.message");
      default -> new ErrorView(status, "error.500.title", "error.500.message");
    };
  }
}
