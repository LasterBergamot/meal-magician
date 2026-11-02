package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;

/**
 * One meal line on a template's page: its slot, the meal's name, whether the meal is archived and
 * the note it was saved with.
 *
 * @param slotName the slot type's name, for example {@code LUNCH}, which the slot chip takes
 * @param mealName the library meal's name
 * @param archived whether that meal is out of the default library (the page shows a badge)
 * @param note the note saved with the meal, or {@code null} when there is none
 */
public record TemplateLine(String slotName, String mealName, boolean archived, String note) {

  /**
   * The message key of the slot's word.
   *
   * @return for example {@code slot.lunch}, to pass through {@code ctx.t}
   */
  public String labelKey() {
    return SlotGroup.labelKeyOf(SlotType.valueOf(slotName));
  }
}
