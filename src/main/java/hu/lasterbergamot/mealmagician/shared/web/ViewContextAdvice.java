package hu.lasterbergamot.mealmagician.shared.web;

import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts a {@link ViewContext} named {@code ctx} into the model of every controller, so no controller
 * has to remember to pass the translation helper, the shell's data or the CSRF token to its
 * templates.
 */
@ControllerAdvice
public class ViewContextAdvice {

  private final Messages messages;
  private final ObjectProvider<CsrfMetaSource> csrfSource;
  private final ObjectProvider<HouseholdNavSource> householdSource;
  private final ObjectProvider<ShellUserSource> userSource;

  /**
   * Creates the advice.
   *
   * @param messages the translation helper
   * @param csrfSource the CSRF token source; absent until Spring Security is configured
   * @param householdSource the shell's household source; absent in slices without the household
   *     module
   * @param userSource the shell's person source; absent in slices without the identity module, in
   *     which case every page is laid out as anonymous
   */
  public ViewContextAdvice(
      Messages messages,
      ObjectProvider<CsrfMetaSource> csrfSource,
      ObjectProvider<HouseholdNavSource> householdSource,
      ObjectProvider<ShellUserSource> userSource) {
    this.messages = messages;
    this.csrfSource = csrfSource;
    this.householdSource = householdSource;
    this.userSource = userSource;
  }

  /**
   * Builds the context for one request.
   *
   * @param request the current request
   * @return the context: {@code csrf} is {@code null} when there is no token source or no token,
   *     {@code household} is {@code null} when there is no household to show, {@code user} is
   *     {@code null} for an anonymous request, {@code admin} is {@code true} for instance admins
   *     and {@code active} is the navigation destination the path belongs to
   */
  @ModelAttribute("ctx")
  public ViewContext viewContext(HttpServletRequest request) {
    CsrfMetaSource csrf = csrfSource.getIfAvailable();
    HouseholdNavSource households = householdSource.getIfAvailable();
    ShellUserSource users = userSource.getIfAvailable();
    return new ViewContext(
        messages,
        csrf == null ? null : csrf.from(request),
        households == null ? null : households.from(request),
        // Answered by Spring Security's request wrapper ("ROLE_" is added); false when anonymous.
        request.isUserInRole("ADMIN"),
        users == null ? null : users.from(request),
        NavDestination.matching(request.getRequestURI()));
  }
}
