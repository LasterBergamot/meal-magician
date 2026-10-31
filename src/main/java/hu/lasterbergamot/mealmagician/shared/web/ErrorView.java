package hu.lasterbergamot.mealmagician.shared.web;

/**
 * View model of the error page and fragment.
 *
 * @param status the HTTP status that is being reported
 * @param titleKey message key of the headline
 * @param messageKey message key of the explanation
 */
public record ErrorView(int status, String titleKey, String messageKey) {}
