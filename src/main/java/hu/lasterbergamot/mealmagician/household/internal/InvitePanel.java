package hu.lasterbergamot.mealmagician.household.internal;

import java.util.List;

/**
 * Everything the owner's "invite someone" section renders. {@code error} is a message key or {@code
 * null}; {@code link} is the full {@code /join/<code>} URL, and is only set in the one response
 * that creates the invite, because the code cannot be shown again.
 *
 * @param rows the household's invites, newest first
 * @param email the text in the e-mail field, kept when the form is rejected
 * @param expiry the selected expiry code ({@code 2h}, {@code 24h} or {@code 3d})
 * @param error message key of the problem to show, or {@code null}
 * @param link the new invite link to show once, or {@code null}
 * @param linkEmail the address the new link is for, shown beside it, or {@code null}
 */
public record InvitePanel(
    List<InviteRow> rows,
    String email,
    String expiry,
    String error,
    String link,
    String linkEmail) {

  /**
   * The same panel with an error and what the person typed.
   *
   * @param typedEmail what the person typed
   * @param typedExpiry the expiry they picked
   * @param errorKey message key of the problem
   * @return the changed panel
   */
  InvitePanel rejected(String typedEmail, String typedExpiry, String errorKey) {
    return new InvitePanel(rows, typedEmail, typedExpiry, errorKey, null, null);
  }

  /**
   * The same panel (already listing the new invite) with the link of a freshly created invite.
   *
   * @param newLink the full join link
   * @param forEmail the invited address
   * @return the changed panel
   */
  InvitePanel created(String newLink, String forEmail) {
    return new InvitePanel(rows, "", expiry, null, newLink, forEmail);
  }
}
