package hu.lasterbergamot.mealmagician.household.internal;

/**
 * What the join page renders: either the household to confirm, or the reason the link was refused.
 *
 * @param code the code from the link, posted back by the confirm button; {@code null} when the link
 *     was refused
 * @param householdName the household the person is about to enter, or {@code null} when refused
 * @param errorKey message key explaining why the link was refused, or {@code null}
 */
public record JoinPage(String code, String householdName, String errorKey) {}
