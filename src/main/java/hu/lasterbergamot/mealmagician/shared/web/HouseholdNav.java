package hu.lasterbergamot.mealmagician.shared.web;

import java.util.List;

/**
 * What the app shell shows about the household the person is working in, and the switcher's list.
 *
 * <p>The list holds only households the person belongs to: the household module builds it from
 * their memberships, so the switcher can never offer someone else's household.
 *
 * @param name the current household's display name, or {@code null} for a signed-in person who has
 *     no household yet (the shell then invites them to create one)
 * @param households the person's households, the current one marked; empty when they have none
 */
public record HouseholdNav(String name, List<HouseholdChoice> households) {

  /** Copies the list so that a caller cannot change it after the view model is built. */
  public HouseholdNav {
    households = List.copyOf(households);
  }

  /**
   * One row of the household switcher.
   *
   * @param id the household's id, which the switch form posts
   * @param name the household's display name
   * @param current whether this is the household the person is working in now
   */
  public record HouseholdChoice(long id, String name, boolean current) {}
}
