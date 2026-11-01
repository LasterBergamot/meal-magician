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

  /**
   * Finishes a form post by sending the browser to another page.
   *
   * <p>An htmx request gets a {@code 200} with {@code HX-Redirect}, which makes htmx load the page
   * as a normal navigation. A plain {@code 302} would not work: the browser follows it inside the
   * fetch and htmx would swap the whole next page into the form's target. A normal request gets the
   * usual {@code 303 See Other}, so the browser asks for the next page with GET.
   *
   * @param request the current request
   * @param response the response, which this method completes
   * @param url where to go
   * @return always {@code null}: a handler returning it, with the response as a parameter, tells
   *     Spring MVC that the response is already complete and no view is rendered
   */
  public static String redirect(
      HttpServletRequest request, HttpServletResponse response, String url) {
    if (isRequest(request)) {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setHeader("HX-Redirect", url);
    } else {
      response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      response.setHeader("Location", url);
    }
    return null;
  }
}
