package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.util.List;

/**
 * One row of the desktop table: a slot type across the seven days of the week.
 *
 * @param type the slot type of the row
 * @param cells the week's seven groups of that type, Monday first
 */
public record SlotRow(SlotType type, List<SlotGroup> cells) {

  /**
   * The slot type's name, which the {@code ui/_slot_chip} component takes.
   *
   * @return {@code BREAKFAST}, {@code LUNCH}, {@code DINNER} or {@code SNACK}
   */
  public String slotName() {
    return type.name();
  }

  /**
   * The message key of the row's header word.
   *
   * @return for example {@code slot.dinner}
   */
  public String labelKey() {
    return SlotGroup.labelKeyOf(type);
  }
}
