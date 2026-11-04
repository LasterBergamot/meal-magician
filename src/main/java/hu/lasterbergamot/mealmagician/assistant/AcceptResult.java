package hu.lasterbergamot.mealmagician.assistant;

/**
 * What accepting a draft did.
 *
 * @param slotsWritten how many library meals were written into the plan
 * @param droppedIdeas how many ideas were left out because nobody added them to the library first
 *     (an idea never reaches the plan by itself)
 */
public record AcceptResult(int slotsWritten, int droppedIdeas) {}
