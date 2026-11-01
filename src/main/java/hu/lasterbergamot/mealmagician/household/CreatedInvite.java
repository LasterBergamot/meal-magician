package hu.lasterbergamot.mealmagician.household;

/**
 * The result of creating an invite: the list entry plus the one and only chance to see the code.
 *
 * @param invite the new invite as it appears in the list
 * @param code the raw code for the {@code /join/<code>} link; show it once and do not store or log
 *     it
 */
public record CreatedInvite(InviteView invite, String code) {}
