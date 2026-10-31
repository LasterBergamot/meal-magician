package hu.lasterbergamot.mealmagician.identity.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the sign-in page, the only page an anonymous visitor sees.
 *
 * <p>The page holds one link to {@code /oauth2/authorization/google}; Spring Security takes it from
 * there. {@code ?error} (set by {@link SignInFailureHandler}) and {@code ?logout} (set by the
 * logout rule in {@link SecurityConfig}) only choose which notice is shown above the button.
 */
@Controller
class LoginController {

  /**
   * Shows the sign-in page, or sends a person who is already signed in to the home page.
   *
   * @param authentication who is asking; anyone not signed in with Google gets the page
   * @param error present after a failed sign-in (for example "Cancel" on Google's consent screen)
   * @param logout present after signing out
   * @param model receives the notice flags for the template
   * @return the view name, or a redirect to {@code /} for a signed-in person
   */
  @GetMapping(SecurityConfig.LOGIN_URL)
  String login(
      Authentication authentication,
      @RequestParam(required = false) String error,
      @RequestParam(required = false) String logout,
      Model model) {
    if (authentication instanceof OAuth2AuthenticationToken) {
      return "redirect:/";
    }
    model.addAttribute("failed", error != null);
    model.addAttribute("signedOut", logout != null);
    return "login/page";
  }
}
