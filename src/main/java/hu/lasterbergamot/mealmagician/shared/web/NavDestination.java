package hu.lasterbergamot.mealmagician.shared.web;

import java.util.Arrays;
import java.util.List;

/**
 * The places the app shell links to: the bottom tab bar on a phone and the sidebar on a desktop
 * both list the destinations that {@link #visible()} returns.
 *
 * <p>A destination whose page does not exist yet is not available and is left out of both
 * navigations. The tab is not dead, it is absent; switching it on is a one-word change here when
 * its phase lands (plan-ui-redesign.md section 4).
 */
public enum NavDestination {

  /** The week planner (Phase 3). */
  PLAN("/plan", "nav.plan", IconName.CALENDAR, true),

  /** The meal library (Phase 2, issue #88). */
  MEALS("/meals", "nav.meals", IconName.MEALS, true),

  /** Dated occasions with their menus (Phase 4, #112). */
  OCCASIONS("/occasions", "nav.occasions", IconName.GIFT, true),

  /** The households page, where the person manages their households and members. */
  HOUSEHOLD("/households", "nav.household", IconName.HOME, true);

  private final String path;
  private final String labelKey;
  private final IconName icon;
  private final boolean available;

  NavDestination(String path, String labelKey, IconName icon, boolean available) {
    this.path = path;
    this.labelKey = labelKey;
    this.icon = icon;
    this.available = available;
  }

  /**
   * The URL the destination links to.
   *
   * @return the path, for example {@code /households}
   */
  public String path() {
    return path;
  }

  /**
   * The message key of the destination's label, to be passed through {@code ctx.t}.
   *
   * @return a key such as {@code nav.household}
   */
  public String labelKey() {
    return labelKey;
  }

  /**
   * The icon drawn next to the label.
   *
   * @return the icon name
   */
  public IconName icon() {
    return icon;
  }

  /**
   * The destinations to show in the navigation, in tab order.
   *
   * @return the available destinations in declaration order (Plan, Meals, Occasions, then
   *     Household)
   */
  public static List<NavDestination> visible() {
    return Arrays.stream(values()).filter(destination -> destination.available).toList();
  }

  /**
   * Finds the destination a request belongs to, so its tab can be highlighted.
   *
   * <p>A destination matches its own path and the pages below it, so {@code /households/members}
   * highlights Household. The address {@code /} only redirects, so it belongs to no destination and
   * highlights nothing. Destinations that are not available match too, so a page reached by URL
   * still gets its highlight once its tab appears.
   *
   * @param requestPath the request's path, for example {@code /households/members}
   * @return the matching destination, or {@code null} when the page is in no navigation (the
   *     sign-in page, a join link)
   */
  public static NavDestination matching(String requestPath) {
    for (NavDestination destination : values()) {
      if (destination.matches(requestPath)) {
        return destination;
      }
    }
    return null;
  }

  private boolean matches(String requestPath) {
    return requestPath.equals(path) || requestPath.startsWith(path + "/");
  }
}
