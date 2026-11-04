package hu.lasterbergamot.mealmagician.assistant;

/**
 * One suggestion for a single meal slot: a library meal and the model's reason for it. The reason
 * is text the model wrote, so a page must escape it like any other user text.
 *
 * @param mealId the library meal; always an active meal of the household (the server checked it)
 * @param mealName the meal's current name
 * @param reason one short sentence on why it fits, or {@code null} when the model gave none
 */
public record SlotSuggestion(long mealId, String mealName, String reason) {}
