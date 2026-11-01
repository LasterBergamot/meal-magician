package hu.lasterbergamot.mealmagician.household;

/**
 * What the join page shows before the person confirms: which household they are about to enter.
 *
 * @param householdId the household the invite is for
 * @param householdName its display name
 */
public record InvitePreview(long householdId, String householdName) {}
