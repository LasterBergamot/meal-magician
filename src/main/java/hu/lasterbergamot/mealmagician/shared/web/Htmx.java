package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Small helpers for the "htmx request gets a fragment, a normal request gets the full page" rule
 * (java-modules.md, web-ui.md).
 */
public final class Htmx {

  /** Header htmx adds to every request it makes. */
  public static final String REQUEST_HEADER = "HX-Request";

  private Htmx() {}

  /**
   * Tells whether htmx (and not the browser's own navigation) sent the request.
   *
   * @param request the current request
   * @return {@code true} if the {@code HX-Request} header is present
   */
  public static boolean isRequest(HttpServletRequest request) {
    return request.getHeader(REQUEST_HEADER) != null;
  }

  /**
   * Picks the view for a URL that serves both a full page and a fragment.
   *
   * <p>It also adds {@code Vary: HX-Request}. Without it a browser or proxy may cache the fragment
   * under the page's URL and show it as the whole page when the user presses "back".
   *
   * @param request the current request
   * @param response the response, which receives the {@code Vary} header
   * @param fragmentView the view name to render for htmx requests (by convention {@code _name})
   * @param pageView the view name to render for normal requests
   * @return {@code fragmentView} for htmx requests, otherwise {@code pageView}
   */
  public static String view(
      HttpServletRequest request,
      HttpServletResponse response,
      String fragmentView,
      String pageView) {
    response.addHeader("Vary", REQUEST_HEADER);
    return isRequest(request) ? fragmentView : pageView;
  }
}
