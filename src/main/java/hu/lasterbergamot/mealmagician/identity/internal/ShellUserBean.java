package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.ShellUser;
import hu.lasterbergamot.mealmagician.shared.web.ShellUserSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feeds the app shell the signed-in person's name (see {@link ShellUserSource}). It reads the name
 * from the session's principal, so no database query runs for it.
 */
@Component
class ShellUserBean implements ShellUserSource {

  /**
   * Builds the shell's view of the person, or {@code null} when nobody is signed in.
   *
   * <p>The name is Google's full name, or the e-mail address when Google sent no name (the same
   * fallback the sign-in uses for the stored name).
   *
   * @param request the current request, not used: the principal lives in the security context
   * @return the person, or {@code null} for an anonymous request
   */
  @Override
  public ShellUser from(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof SignedInUser user)) {
      return null;
    }
    String name = user.getFullName() != null ? user.getFullName() : user.getEmail();
    return new ShellUser(name);
  }
}
