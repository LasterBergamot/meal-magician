package hu.lasterbergamot.mealmagician.identity;

/**
 * What other modules may know about a person: enough to show them in a list, nothing more.
 *
 * @param id the {@code app_user} id
 * @param displayName the name Google reported at the person's last sign-in
 * @param email the verified, normalized e-mail address
 */
public record UserProfile(long id, String displayName, String email) {}
