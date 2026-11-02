package hu.lasterbergamot.mealmagician.planning;

/**
 * One plan template as the template list shows it, without its items.
 *
 * @param id the template's id, used in links and in the actions on it
 * @param name the trimmed name, 1 to 100 characters
 * @param scope whether it covers one day or one week
 * @param itemCount how many meals it holds
 * @param version the optimistic-lock version; a page sends it back with a rename or delete
 */
public record PlanTemplateSummary(
    long id, String name, TemplateScope scope, int itemCount, long version) {}
