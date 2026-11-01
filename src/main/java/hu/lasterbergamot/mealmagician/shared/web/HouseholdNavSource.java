package hu.lasterbergamot.mealmagician.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the current household to the layout's header.
 *
 * <p>The {@code household} module publishes one bean of this type. The indirection exists because
 * {@code shared} may not depend on a business module (it would be a cycle), the same way {@link
 * CsrfMetaSource} keeps {@code shared} free of Spring Security.
 */
@FunctionalInterface
public interface HouseholdNavSource {

  /**
   * Reads the household for a request.
   *
   * @param request the current request
   * @return the header data, or {@code null} for an anonymous request
   */
  HouseholdNav from(HttpServletRequest request);
}
