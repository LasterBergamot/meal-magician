package hu.lasterbergamot.mealmagician.household;

/**
 * One member of a household as the members page lists them.
 *
 * @param userId the {@code app_user} id, which the remove and transfer actions are addressed by
 * @param displayName the member's name
 * @param email the member's e-mail address
 * @param role the member's role in this household
 */
public record MemberView(long userId, String displayName, String email, HouseholdRole role) {}
