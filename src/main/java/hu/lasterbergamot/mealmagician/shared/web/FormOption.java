package hu.lasterbergamot.mealmagician.shared.web;

/**
 * One choice of the {@code ui/_select} fragment: the {@code value} the form posts and the {@code
 * label} the person reads.
 *
 * <p>The label is already translated; the caller takes it from {@code ctx.t}, the way it passes
 * every visible text into a component. The record only carries the two strings, so a page can list
 * its choices in one place.
 *
 * @param value the value sent with the form when this choice is picked
 * @param label the text shown in the list, already translated
 */
public record FormOption(String value, String label) {}
