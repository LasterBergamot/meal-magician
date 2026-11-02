package hu.lasterbergamot.mealmagician.planning.internal;

/**
 * One row of the template list: a template's name, how many meals it holds and where its page
 * lives. The list shows only these three things; the meals are read on the template's own page.
 *
 * @param id the template's id, used to build its link
 * @param name the template's name, already trimmed
 * @param mealCount how many meals the template holds, shown as "Meals: n"
 */
public record TemplateRow(long id, String name, int mealCount) {

  /**
   * The address of the template's page.
   *
   * @return for example {@code /plan/templates/7}
   */
  public String href() {
    return "/plan/templates/" + id;
  }
}
