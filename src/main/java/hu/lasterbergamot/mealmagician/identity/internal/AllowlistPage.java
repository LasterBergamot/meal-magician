package hu.lasterbergamot.mealmagician.identity.internal;

import java.util.List;

/**
 * Everything the allowlist admin page renders.
 *
 * @param rows the people who may sign in
 * @param email the text in the add field, kept when the form is rejected
 * @param error message key of the problem to show, or {@code null}
 */
public record AllowlistPage(List<AllowlistRow> rows, String email, String error) {}
