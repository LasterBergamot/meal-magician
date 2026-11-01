package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdSummary;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Works out which household the current request is about (PLAN.md section 4.3, step 3).
 *
 * <p>The chosen household's id is kept in the HTTP session, but the id is never trusted: it is
 * checked against the person's memberships on <em>every</em> request, so someone who was removed
 * from a household loses access at once, and a tampered session value finds nothing. When the
 * session holds no valid choice the person's first household (by name) is used and remembered, so
 * someone who has just joined a household needs no extra click.
 *
 * <p>The answer is cached on the request, because the header, the controller and the argument
 * resolver all ask during one request.
 */
@Component
public class CurrentHouseholdResolver {

  /** Session attribute holding the id of the chosen household. */
  static final String SESSION_KEY = "mm.currentHouseholdId";

  private static final String REQUEST_KEY = CurrentHouseholdResolver.class.getName() + ".resolved";

  private final HouseholdService households;

  /**
   * Creates the resolver.
   *
   * @param households the household service, which does the membership check
   */
  public CurrentHouseholdResolver(HouseholdService households) {
    this.households = households;
  }

  /**
   * The signed-in person, as Spring Security knows them for this thread.
   *
   * @return the person, or empty for an anonymous request
   */
  static Optional<SignedInUser> signedInUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
      return Optional.of(user);
    }
    return Optional.empty();
  }

  /**
   * Finds the household this request works on.
   *
   * @param request the current request, whose session holds the choice
   * @return the household as the signed-in person sees it; empty for an anonymous request or a
   *     person who belongs to no household
   */
  @SuppressWarnings("unchecked")
  public Optional<CurrentHousehold> resolve(HttpServletRequest request) {
    Object cached = request.getAttribute(REQUEST_KEY);
    if (cached != null) {
      return (Optional<CurrentHousehold>) cached;
    }
    Optional<CurrentHousehold> resolved = signedInUser().flatMap(user -> lookUp(request, user));
    request.setAttribute(REQUEST_KEY, resolved);
    return resolved;
  }

  /**
   * Remembers the household the person works in from now on. The caller has already checked the
   * membership, for example with {@link HouseholdService#require}.
   *
   * @param request the current request, whose session receives the choice
   * @param householdId the chosen household
   */
  public void select(HttpServletRequest request, long householdId) {
    request.getSession().setAttribute(SESSION_KEY, householdId);
    request.removeAttribute(REQUEST_KEY);
  }

  private Optional<CurrentHousehold> lookUp(HttpServletRequest request, SignedInUser user) {
    HttpSession session = request.getSession();
    if (session.getAttribute(SESSION_KEY) instanceof Long chosen) {
      Optional<CurrentHousehold> found = households.find(chosen, user.userId());
      if (found.isPresent()) {
        return found;
      }
      session.removeAttribute(SESSION_KEY);
    }
    List<HouseholdSummary> mine = households.listFor(user.userId());
    if (mine.isEmpty()) {
      return Optional.empty();
    }
    HouseholdSummary first = mine.get(0);
    session.setAttribute(SESSION_KEY, first.id());
    return Optional.of(new CurrentHousehold(first.id(), first.name(), first.role()));
  }
}
