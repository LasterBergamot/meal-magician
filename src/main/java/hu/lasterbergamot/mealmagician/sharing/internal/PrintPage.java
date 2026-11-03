package hu.lasterbergamot.mealmagician.sharing.internal;

/**
 * Everything the print page renders: the week with its notes and the way back to the plan.
 *
 * @param week the week as the shared days template draws it; unlike the public page it carries the
 *     slot notes
 * @param backHref the address of the same week on the plan page, for the "Back to the plan" link
 */
public record PrintPage(SharedWeekPage week, String backHref) {}
