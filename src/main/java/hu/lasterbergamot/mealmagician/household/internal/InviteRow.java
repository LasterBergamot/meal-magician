package hu.lasterbergamot.mealmagician.household.internal;

/**
 * One line of the owner's invite list, ready to render.
 *
 * @param id the invite id, for the revoke button
 * @param email the invited address
 * @param statusKey message key naming the status ({@code household.invite.status.pending}, ...)
 * @param expires when the link stops working, already formatted in the household's time zone
 * @param revocable whether a revoke button makes sense, which is only while it is pending
 */
public record InviteRow(
    long id, String email, String statusKey, String expires, boolean revocable) {}
