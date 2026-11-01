package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import java.util.List;

/**
 * Everything the members page and its fragment render.
 *
 * @param household the household, with the viewer's role <em>now</em> (it changes when they hand
 *     the household over, so it is read again after every action)
 * @param rows the members, owners first
 * @param error message key of a refused action, or {@code null}
 */
public record MembersPage(CurrentHousehold household, List<MemberRow> rows, String error) {}
