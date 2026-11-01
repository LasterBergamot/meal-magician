package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdSummary;
import java.util.List;

/**
 * Everything the households page and its form fragments render. Error fields hold a message key
 * (translated in the template) or {@code null}; the name fields hold what the person typed, so a
 * rejected form keeps its input.
 *
 * @param households the person's households
 * @param current the household they work in now, or {@code null} if they have none
 * @param createName the text in the "create" form
 * @param createError message key for the "create" form, or {@code null}
 * @param renameName the text in the "rename" form
 * @param renameError message key for the "rename" form, or {@code null}
 * @param invites the owner's invite section, or {@code null} when the person is not an owner of the
 *     current household
 */
public record HouseholdsPage(
    List<HouseholdSummary> households,
    CurrentHousehold current,
    String createName,
    String createError,
    String renameName,
    String renameError,
    InvitePanel invites) {

  /**
   * The page with the "create" form rejected.
   *
   * @param name what the person typed
   * @param errorKey message key of the problem
   * @return the changed page
   */
  HouseholdsPage withCreateError(String name, String errorKey) {
    return new HouseholdsPage(
        households, current, name, errorKey, renameName, renameError, invites);
  }

  /**
   * The page with the "rename" form rejected.
   *
   * @param name what the person typed
   * @param errorKey message key of the problem
   * @return the changed page
   */
  HouseholdsPage withRenameError(String name, String errorKey) {
    return new HouseholdsPage(
        households, current, createName, createError, name, errorKey, invites);
  }

  /**
   * The page with another invite section.
   *
   * @param panel the section to show
   * @return the changed page
   */
  HouseholdsPage withInvites(InvitePanel panel) {
    return new HouseholdsPage(
        households, current, createName, createError, renameName, renameError, panel);
  }
}
