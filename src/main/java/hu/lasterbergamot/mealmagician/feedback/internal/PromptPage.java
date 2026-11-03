package hu.lasterbergamot.mealmagician.feedback.internal;

/**
 * The "How did it go?" card on the plan page: one past meal that the member has not rated yet.
 *
 * @param slotId the slot to rate
 * @param mealName the meal's name
 * @param dayLabel the translated day, for example {@code Hétfő, nov. 2.}
 * @param slotName the slot type's enum name, for the slot chip
 * @param slotKey the message key of the slot type's word, for example {@code slot.lunch}
 */
public record PromptPage(
    long slotId, String mealName, String dayLabel, String slotName, String slotKey) {

  /**
   * The address of the slot sheet, which holds the rating form.
   *
   * @return for example {@code /plan/slots/7}
   */
  public String slotHref() {
    return "/plan/slots/" + slotId;
  }
}
