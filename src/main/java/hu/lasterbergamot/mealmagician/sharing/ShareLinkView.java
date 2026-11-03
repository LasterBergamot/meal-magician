package hu.lasterbergamot.mealmagician.sharing;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A share link as a member sees it in the list. It never carries the token or its hash: the token
 * is shown once at creation and only its hash is stored.
 *
 * @param id the link id, used to revoke it
 * @param weekStart the Monday of the shared week
 * @param status where the link stands now
 * @param createdAt when the link was made
 * @param expiresAt when the link stops working
 */
public record ShareLinkView(
    long id, LocalDate weekStart, ShareLinkStatus status, Instant createdAt, Instant expiresAt) {}
