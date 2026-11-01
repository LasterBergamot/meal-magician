package hu.lasterbergamot.mealmagician.household;

import java.time.Instant;

/**
 * An invite as the owner sees it in the list. It never carries the code: the code is shown once at
 * creation and only its hash is stored.
 *
 * @param id the invite id, used to revoke it
 * @param email the invited address, lower-case
 * @param status where the invite stands now
 * @param expiresAt when the link stops working
 */
public record InviteView(long id, String email, InviteStatus status, Instant expiresAt) {}
