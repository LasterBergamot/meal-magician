package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.i18n.LanguageStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Saves a language switch in {@code app_user.locale} when the person making it is signed in, so the
 * choice comes back after their next sign-in (see {@link SignInLanguage}).
 *
 * <p>An anonymous switch (the sign-in page) changes only the cookie: there is no row to save to.
 */
@Component
class StoredLanguage implements LanguageStore {

  private final AppUserService users;

  /**
   * Creates the store.
   *
   * @param users saves the choice on the person's row
   */
  StoredLanguage(AppUserService users) {
    this.users = users;
  }

  /**
   * Saves the language for the signed-in person, if the request has one.
   *
   * @param request the request that asked for the switch
   * @param locale the supported language the person picked
   */
  @Override
  public void remember(HttpServletRequest request, Locale locale) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
      users.chooseLocale(user.userId(), locale.getLanguage());
    }
  }
}
