package hu.lasterbergamot.mealmagician.household.internal;

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
    long userId, String name, String email, boolean owner, boolean self, boolean manageable) {}
