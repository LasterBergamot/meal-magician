package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.List;

/**
 * The model of one occasion template's page: what it holds (kind, notes, menu), the "Plan it again"
 * form and the delete action.
 *
 * @param id the template's id
 * @param name the template's name, which becomes the new occasion's name
 * @param type the kind of occasion it makes
 * @param notes the notes the new occasion gets, or {@code null}
 * @param menu the meals in order
 * @param useDate the text of the date field; empty for a fresh page, what was typed after a
 *     rejection
 * @param useError the message key shown under the date field, or {@code null}
 */
public record OccasionTemplatePage(
    long id,
    String name,
    OccasionType type,
    String notes,
    List<MealLine> menu,
    String useDate,
    String useError) {

  /**
   * The address of the template's page.
   *
   * @return for example {@code /occasions/templates/4}
   */
  public String href() {
    return "/occasions/templates/" + id;
  }

  /**
   * The address the "Plan it again" form posts to.
   *
   * @return for example {@code /occasions/templates/4/use}
   */
  public String useAction() {
    return href() + "/use";
  }

  /**
   * The address the Delete confirm posts to.
   *
   * @return for example {@code /occasions/templates/4/delete}
   */
  public String deleteAction() {
    return href() + "/delete";
  }

  /**
   * The message key of the kind's label.
   *
   * @return for example {@code meal.occasion.holiday}
   */
  public String typeKey() {
    return OccasionTypes.key(type);
  }

  /**
   * The same page with the date field showing a rejection.
   *
   * @param date the date as typed
   * @param errorKey the message key of the problem
   * @return a copy with {@code useDate} and {@code useError} replaced
   */
  public OccasionTemplatePage withUse(String date, String errorKey) {
    return new OccasionTemplatePage(id, name, type, notes, menu, date, errorKey);
  }
}
