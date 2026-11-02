package hu.lasterbergamot.mealmagician.planning.internal;

import java.util.List;

/**
 * The model of the Occasions list. The nearest upcoming occasion is shown as a card of its own; the
 * later ones and the past ones follow as rows. Both lists come from {@link OccasionPageFactory},
 * which decides the order.
 *
 * @param next the nearest occasion on or after today, or {@code null} when there is none
 * @param upcoming the later occasions, earliest first (without {@code next})
 * @param past the occasions before today, newest first
 * @param templates the household's saved occasion menus, by name; shown whether or not there are
 *     occasions
 */
public record OccasionsPage(
    OccasionCard next,
    List<OccasionCard> upcoming,
    List<OccasionCard> past,
    List<OccasionTemplateRow> templates) {

  /**
   * Whether the household has no occasions at all, so the page shows the empty state. Templates do
   * not count: a household with only templates still sees them below the empty state.
   *
   * @return {@code true} when there is no next, upcoming or past occasion
   */
  public boolean empty() {
    return next == null && past.isEmpty();
  }
}
