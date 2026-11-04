package hu.lasterbergamot.mealmagician.assistant.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the model must answer with for a one-slot suggestion: up to three library meals, as JSON.
 * Spring AI's {@code BeanOutputConverter} turns this record into the schema in the prompt, so the
 * description texts are instructions to the model, in English. Nothing in it is trusted: {@link
 * DraftGenerator} checks every id against the household's active meals and cleans every reason.
 *
 * @param picks the suggested meals, best first
 */
public record SlotProposal(
    @JsonPropertyDescription("At most 3 library meals that suit the slot, best first")
        List<Pick> picks) {

  /**
   * One suggested library meal.
   *
   * @param mealId the id of a meal that a tool returned
   * @param reason one short sentence on why it suits the slot, in the member's language
   */
  public record Pick(
      @JsonPropertyDescription("Id of a library meal returned by a tool") Long mealId,
      @JsonPropertyDescription("One short sentence on why it suits this slot") String reason) {}
}
