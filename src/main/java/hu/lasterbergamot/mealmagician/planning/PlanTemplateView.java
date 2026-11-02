package hu.lasterbergamot.mealmagician.planning;

import java.util.List;

/**
 * One plan template with its items, for the template's detail page.
 *
 * @param id the template's id
 * @param name the trimmed name
 * @param scope whether it covers one day or one week
 * @param items the meals, ordered by day offset, then slot type (breakfast, lunch, dinner, snack),
 *     then position, then id
 * @param version the optimistic-lock version; a page sends it back with a rename or delete
 */
public record PlanTemplateView(
    long id, String name, TemplateScope scope, List<PlanTemplateItemView> items, long version) {}
