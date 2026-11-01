package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.InviteExpiry;
import hu.lasterbergamot.mealmagician.household.InviteService;
import hu.lasterbergamot.mealmagician.household.InviteStatus;
import hu.lasterbergamot.mealmagician.household.InviteView;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the model of the households page, shared by the controllers that answer with it or one of
 * its fragments, so each of them starts from the same complete, current page.
 */
@Component
class HouseholdsPageFactory {

  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final HouseholdService households;
  private final InviteService invites;
  private final CurrentHouseholdResolver resolver;
  private final Clock clock;

  HouseholdsPageFactory(
      HouseholdService households,
      InviteService invites,
      CurrentHouseholdResolver resolver,
      Clock clock) {
    this.households = households;
    this.invites = invites;
    this.resolver = resolver;
    this.clock = clock;
  }

  /**
   * The page with empty forms: the person's households, and the invite section if they own the
   * current household.
   *
   * @param user the signed-in person
   * @param request the current request, which tells the current household
   * @return the page model
   */
  HouseholdsPage page(SignedInUser user, HttpServletRequest request) {
    CurrentHousehold current = resolver.resolve(request).orElse(null);
    InvitePanel panel = null;
    if (current != null && current.isOwner()) {
      panel =
          new InvitePanel(
              rows(invites.list(current.id(), user.userId())),
              "",
              InviteExpiry.HOURS_24.code(),
              null,
              null,
              null);
    }
    return new HouseholdsPage(
        households.listFor(user.userId()),
        current,
        "",
        null,
        current == null ? "" : current.name(),
        null,
        panel);
  }

  /**
   * Turns the service's invite list into display rows.
   *
   * @param views the invites, newest first
   * @return the rows
   */
  List<InviteRow> rows(List<InviteView> views) {
    return views.stream().map(this::row).toList();
  }

  private InviteRow row(InviteView view) {
    return new InviteRow(
        view.id(),
        view.email(),
        "household.invite.status." + view.status().name().toLowerCase(Locale.ROOT),
        WHEN.format(view.expiresAt().atZone(clock.getZone())),
        view.status() == InviteStatus.PENDING);
  }
}
