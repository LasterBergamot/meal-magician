package hu.lasterbergamot.mealmagician.sharing;

/**
 * The result of creating a share link: the list entry plus the one and only chance to see the
 * token.
 *
 * @param link the new link as it appears in the list
 * @param token the raw token for the {@code /share/<token>} link; show it once and do not store or
 *     log it
 */
public record CreatedShareLink(ShareLinkView link, String token) {}
