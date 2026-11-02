package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.Locale;

/**
 * Names the message-bundle key for each {@link OccasionType}, so the templates can write {@code
 * ctx.t(OccasionLabels.key(type))}. The key is built here, not in the template, because the
 * template guard ({@code UiGuardTest}) checks only literal keys; a test checks that every key built
 * here exists in both bundles instead.
 */
public final class OccasionLabels {

  private OccasionLabels() {}

  /**
   * The message key of an occasion type's label.
   *
   * @param type the occasion type
   * @return for example {@code meal.occasion.birthday}
   */
  public static String key(OccasionType type) {
    return "meal.occasion." + type.name().toLowerCase(Locale.ROOT);
  }
}
