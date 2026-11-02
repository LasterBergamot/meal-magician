package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
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
 */
public record OccasionDetailPage(
    long id,
    String name,
    String dateLabel,
    OccasionType type,
    String notes,
    List<MenuLine> menu,
    long version,
    boolean conflict) {

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
    return new OccasionDetailPage(id, name, dateLabel, type, notes, menu, version, true);
  }
}
