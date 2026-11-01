package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.shared.web.HouseholdNav;
import hu.lasterbergamot.mealmagician.shared.web.HouseholdNavSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Feeds the page header (see {@link HouseholdNavSource}): the name of the household the person is
 * working in, or the invitation to create one.
 */
@Component
class HouseholdNavBean implements HouseholdNavSource {

  private final CurrentHouseholdResolver resolver;

  HouseholdNavBean(CurrentHouseholdResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public HouseholdNav from(HttpServletRequest request) {
    if (CurrentHouseholdResolver.signedInUser().isEmpty()) {
      return null;
    }
    return new HouseholdNav(resolver.resolve(request).map(h -> h.name()).orElse(null));
  }
}
