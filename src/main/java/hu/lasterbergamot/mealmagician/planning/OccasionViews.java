package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.planning.internal.Occasion;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionItem;
import java.util.List;

/**
 * Turns occasion entities into the public view records. Shared by {@link OccasionService} and
 * {@link OccasionTemplateService} (whose {@code use} returns an {@link OccasionView}) so the two
 * build the view the same way. Package-private: other modules only see the records.
 */
final class OccasionViews {

  private OccasionViews() {}

  /**
   * The occasion with its menu.
   *
   * @param o the occasion
   * @param items its menu items, already in menu order
   * @return the view
   */
  static OccasionView occasion(Occasion o, List<OccasionItem> items) {
    return new OccasionView(
        o.getId(),
        o.getName(),
        o.getOccasionDate(),
        o.getType(),
        o.getNotes(),
        items.stream().map(OccasionViews::item).toList(),
        o.getVersion());
  }

  /**
   * The occasion without its menu, with the number of items on it.
   *
   * @param o the occasion
   * @param itemCount how many items are on its menu
   * @return the view
   */
  static OccasionSummary summary(Occasion o, int itemCount) {
    return new OccasionSummary(
        o.getId(), o.getName(), o.getOccasionDate(), o.getType(), itemCount, o.getVersion());
  }

  /**
   * One menu item.
   *
   * @param i the menu item
   * @return the view
   */
  static MenuItemView item(OccasionItem i) {
    return new MenuItemView(i.getId(), i.getPosition(), i.getMealId());
  }
}
