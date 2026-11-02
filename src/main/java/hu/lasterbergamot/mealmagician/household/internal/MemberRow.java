package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.shared.web.ShellUser;

/**
 * One line of the members page.
 *
 * @param userId the {@code app_user} id the remove and transfer buttons post to
 * @param name the member's name
 * @param email the member's e-mail address
 * @param owner whether the member is an owner
 * @param self whether this is the person looking at the page
 * @param manageable whether the viewer may remove this member or hand the household over to them:
 *     the viewer is an owner and the row is somebody else
 */
public record MemberRow(
    long userId, String name, String email, boolean owner, boolean self, boolean manageable) {

  /**
   * The one or two letters on the member's avatar. It reuses the rule of the app shell's own avatar
   * ({@link ShellUser#initials()}), so a person's initials are the same wherever they appear.
   *
   * @return the initials, for example {@code KA} for "Kovács Anna", or {@code ?} for a name with no
   *     letters
   */
  public String initials() {
    return new ShellUser(name).initials();
  }
}
