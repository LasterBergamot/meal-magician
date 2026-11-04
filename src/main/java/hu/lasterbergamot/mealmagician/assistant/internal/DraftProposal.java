package hu.lasterbergamot.mealmagician.assistant.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;

/**
 * What the model must answer with: a week draft as JSON. Spring AI's {@code BeanOutputConverter}
 * turns this record into the JSON schema it puts in the prompt and parses the answer back, so the
 * {@link JsonPropertyDescription} texts are instructions to the model, in English.
 *
 * <p>Nothing in it is trusted. {@link DraftValidator} checks every meal id against the catalog,
 * every date against the week and trims every text before anything is stored.
 *
 * @param summary one or two sentences saying what the week looks like, in the member's language
 * @param items the proposed meals
 */
public record DraftProposal(
    @JsonPropertyDescription("One or two sentences summarising the week, in the member's language")
        String summary,
    @JsonPropertyDescription("The proposed meals, one entry per meal and slot")
        List<ProposedItem> items) {

  /**
   * One proposed meal: either a library meal ({@code mealId}) or a new idea ({@code idea}), never
   * both.
   *
   * @param date the day, as {@code yyyy-MM-dd}, inside the week to plan
   * @param slotType BREAKFAST, LUNCH, DINNER or SNACK
   * @param mealId the id of a meal that a tool returned, or null when proposing an idea
   * @param idea a meal that is not in the library, or null when {@code mealId} is given
   */
  public record ProposedItem(
      @JsonPropertyDescription("The day as yyyy-MM-dd, inside the week to plan") LocalDate date,
      @JsonPropertyDescription("BREAKFAST, LUNCH, DINNER or SNACK") SlotType slotType,
      @JsonPropertyDescription(
              "Id of a library meal returned by a tool; null when proposing a new idea")
          Long mealId,
      @JsonPropertyDescription("A new meal that is not in the library; null when mealId is given")
          Idea idea) {}

  /**
   * A meal the model thinks of that is not in the library. It is shown to the members as a card
   * they may add to the library; it never reaches the plan by itself.
   *
   * @param name the meal's name, short
   * @param reason why it suits this household, one short sentence
   * @param tags a few short tags, may be empty
   * @param ingredients the main ingredients, names only, may be empty
   */
  public record Idea(
      @JsonPropertyDescription("Short name of the meal") String name,
      @JsonPropertyDescription("One short sentence on why it suits this household") String reason,
      @JsonPropertyDescription("A few short tags such as 'quick' or 'soup'") List<String> tags,
      @JsonPropertyDescription("Main ingredients, names only") List<String> ingredients) {}
}
