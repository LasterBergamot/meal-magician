package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;

/**
 * The template list page: the household's day templates and week templates, each already sorted by
 * name by {@code PlanTemplateService.list}. The two lists are kept apart because a day and a week
 * are different things to apply.
 *
 * @param days the day templates
 * @param weeks the week templates
 */
public record TemplatesPage(List<TemplateRow> days, List<TemplateRow> weeks) {

  /**
   * Tells whether the household has no template at all. The page then shows one empty state with a
   * link to the plan, instead of two empty lists.
   *
   * @return {@code true} when both lists are empty
   */
  public boolean empty() {
    return days.isEmpty() && weeks.isEmpty();
  }
}
