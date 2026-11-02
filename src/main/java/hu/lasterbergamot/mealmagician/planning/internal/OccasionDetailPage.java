package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import java.util.List;

/**
 * The model of one occasion's detail page: its values, the menu in order, and the version that the
 * Edit and Delete actions send back so a stale change is noticed.
 *
 * @param id the occasion's id
 * @param name the name
 * @param dateLabel the date written out for the reader
 * @param type the kind of occasion
 * @param notes the notes, or {@code null} when there are none
 * @param menu the meals in order; empty when the menu has none
 * @param version the optimistic-lock version
 * @param conflict whether the page is drawn after a stale delete (the banner says so)
 * @param saveName the text of the "Save as a template" name field: the occasion's own name for a
 *     fresh page, what was typed after a rejected save
 * @param saveError the message key shown under that field, or {@code null}
 */
public record OccasionDetailPage(
    long id,
    String name,
    String dateLabel,
    OccasionType type,
    String notes,
    List<MenuLine> menu,
    long version,
    boolean conflict,
    String saveName,
    String saveError) {

  /**
   * The address of the occasion's detail page.
   *
   * @return for example {@code /occasions/7}
   */
  public String href() {
    return "/occasions/" + id;
  }

  /**
   * The address of the edit form.
   *
   * @return for example {@code /occasions/7/edit}
   */
  public String editHref() {
    return href() + "/edit";
  }

  /**
   * The address the Delete confirm posts to. The version rides in the query string, so the server
   * can refuse a delete of an occasion that changed since the page was drawn.
   *
   * @return for example {@code /occasions/7/delete?version=2}
   */
  public String deleteAction() {
    return href() + "/delete?version=" + version;
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
   * The same page with the conflict banner on: a stale delete answers with the detail page and the
   * banner, so the person sees what happened and can reload.
   *
   * @return a copy with {@code conflict} set to {@code true}
   */
  public OccasionDetailPage withConflict() {
    return new OccasionDetailPage(
        id, name, dateLabel, type, notes, menu, version, true, saveName, saveError);
  }

  /**
   * The address of the pick sheet's content (and of its no-JavaScript page).
   *
   * @return for example {@code /occasions/7/pick}
   */
  public String pickHref() {
    return href() + "/pick";
  }

  /**
   * The address a menu line's Move up or Move down form posts to.
   *
   * @param itemId the menu item to move
   * @return for example {@code /occasions/7/menu/12/move}
   */
  public String moveAction(long itemId) {
    return href() + "/menu/" + itemId + "/move";
  }

  /**
   * The address a menu line's Remove confirm posts to.
   *
   * @param itemId the menu item to remove
   * @return for example {@code /occasions/7/menu/12/remove}
   */
  public String removeAction(long itemId) {
    return href() + "/menu/" + itemId + "/remove";
  }

  /**
   * The address the "Save as a template" form posts to.
   *
   * @return for example {@code /occasions/7/template}
   */
  public String saveAction() {
    return href() + "/template";
  }

  /**
   * Whether the menu can be saved as a template. An empty menu has nothing to save, so the page
   * does not offer the form.
   *
   * @return {@code true} when the menu has at least one meal
   */
  public boolean canSave() {
    return !menu.isEmpty();
  }

  /**
   * The same page with the save form showing a rejection: the typed name is kept and the message is
   * shown under the field.
   *
   * @param typed the name as typed
   * @param errorKey the message key of the problem
   * @return a copy with {@code saveName} and {@code saveError} replaced
   */
  public OccasionDetailPage withSave(String typed, String errorKey) {
    return new OccasionDetailPage(
        id, name, dateLabel, type, notes, menu, version, conflict, typed, errorKey);
  }

  /**
   * The message key for a refused template save.
   *
   * @param problem what the planner refused
   * @return {@code occasion.template.error.empty} for an empty menu, {@code
   *     occasion.template.error.name.long} for a long name, otherwise {@code
   *     occasion.template.error.name.required}
   */
  public static String saveErrorKey(PlanProblem problem) {
    return switch (problem) {
      case NOTHING_TO_COPY -> "occasion.template.error.empty";
      case NAME_TOO_LONG -> "occasion.template.error.name.long";
      default -> "occasion.template.error.name.required";
    };
  }
}
