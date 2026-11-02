package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdSummary;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.shared.web.HouseholdNav;
import hu.lasterbergamot.mealmagician.shared.web.HouseholdNav.HouseholdChoice;
import hu.lasterbergamot.mealmagician.shared.web.HouseholdNavSource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Feeds the app shell (see {@link HouseholdNavSource}): the name of the household the person is
 * working in, or the invitation to create one, and the list the household switcher offers.
 *
 * <p>The list comes from the person's memberships only ({@link HouseholdService#listFor}), so the
 * switcher can never show or switch to a household they do not belong to.
 */
@Component
class HouseholdNavBean implements HouseholdNavSource {

  private final CurrentHouseholdResolver resolver;
  private final HouseholdService households;

  HouseholdNavBean(CurrentHouseholdResolver resolver, HouseholdService households) {
    this.resolver = resolver;
    this.households = households;
  }

  /**
   * Builds the shell's household data for a request.
   *
   * @param request the current request, which tells the current household
   * @return the household data, or {@code null} for an anonymous request
   */
  @Override
  public HouseholdNav from(HttpServletRequest request) {
    Optional<SignedInUser> user = CurrentHouseholdResolver.signedInUser();
    if (user.isEmpty()) {
      return null;
    }
    Optional<CurrentHousehold> current = resolver.resolve(request);
    String name = current.map(CurrentHousehold::name).orElse(null);
    long currentId = current.map(CurrentHousehold::id).orElse(-1L);
    List<HouseholdChoice> choices =
        households.listFor(user.get().userId()).stream()
            .map(summary -> choice(summary, currentId))
            .toList();
    return new HouseholdNav(name, choices);
  }

  private static HouseholdChoice choice(HouseholdSummary summary, long currentId) {
    return new HouseholdChoice(summary.id(), summary.name(), summary.id() == currentId);
  }
}
