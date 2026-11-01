package hu.lasterbergamot.mealmagician.identity.internal;

/**
 * One line of the admin page's list, ready to render.
 *
 * @param email the address
 * @param originKey message key naming where the entry comes from
 * @param added when it was added, already formatted, or an empty string for a bootstrap admin
 * @param removable whether a remove button makes sense
 */
public record AllowlistRow(String email, String originKey, String added, boolean removable) {}
