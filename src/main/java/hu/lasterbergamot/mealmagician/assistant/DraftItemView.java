package hu.lasterbergamot.mealmagician.assistant;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;

/**
 * One proposed meal of a draft as a page shows it: either a library meal (with its current name) or
 * an idea that is not in the library yet. The idea fields are text the model wrote, so a page must
 * escape them like any other user text.
 *
 * @param id the item's id, used by "remove" and "add to library"
 * @param date the day
 * @param slotType the slot type
 * @param position the order inside its day and slot type, counted from 0
 * @param mealId the library meal, or {@code null} for an unresolved idea
 * @param mealName the library meal's name; {@code null} for an idea
 * @param ideaName the idea's name; {@code null} for a library item
 * @param ideaReason why the model suggests it; {@code null} for a library item or when none given
 * @param ideaTags the idea's tag names; empty for a library item
 * @param ideaIngredients the idea's ingredient names; empty for a library item
 */
public record DraftItemView(
    long id,
    LocalDate date,
    SlotType slotType,
    int position,
    Long mealId,
    String mealName,
    String ideaName,
    String ideaReason,
    List<String> ideaTags,
    List<String> ideaIngredients) {

  /**
   * Whether this item is an idea that still has to be added to the library before it can be
   * planned.
   *
   * @return {@code true} for an unresolved idea
   */
  public boolean idea() {
    return mealId == null;
  }
}
