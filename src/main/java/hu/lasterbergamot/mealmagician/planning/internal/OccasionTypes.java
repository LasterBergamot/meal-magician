package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.Locale;

/**
 * Message keys for {@link OccasionType}. The planning pages reuse the catalog's {@code
 * meal.occasion.*} labels (birthday, holiday, party, other) so the words stay in one place; the
 * catalog's own {@code OccasionLabels} is in its {@code internal} package and off-limits across
 * modules.
 */
final class OccasionTypes {

  private OccasionTypes() {}

  /**
   * The message-bundle key of the label for an occasion type.
   *
   * @param type the occasion type
   * @return for example {@code meal.occasion.birthday}
   */
  static String key(OccasionType type) {
    return "meal.occasion." + type.name().toLowerCase(Locale.ROOT);
  }
}
