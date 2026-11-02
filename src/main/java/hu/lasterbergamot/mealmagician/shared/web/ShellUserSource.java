package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the signed-in person to the app shell, through {@link ViewContext#user()}.
 *
 * <p>The {@code identity} module publishes the one bean of this type. The indirection exists
 * because {@code shared} may not depend on {@code identity} (that would be a cycle); the same
 * pattern serves {@link HouseholdNavSource} and {@link CsrfMetaSource}.
 */
@FunctionalInterface
public interface ShellUserSource {

  /**
   * Reads the person for a request.
   *
   * @param request the current request
   * @return the person, or {@code null} for an anonymous request, which gets the plain layout
   */
  ShellUser from(HttpServletRequest request);
}
