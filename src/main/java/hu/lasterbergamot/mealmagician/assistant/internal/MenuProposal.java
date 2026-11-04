package hu.lasterbergamot.mealmagician.assistant.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The JSON the model answers with for an occasion menu. Spring AI's output converter reads it with
 * the schema the {@link JsonPropertyDescription} texts describe; the server then checks every id
 * against the household's live meals before anything is shown ({@code
 * AssistantService.suggestMenu}).
 *
 * @param mealIds the library meal ids the model picked, best first; unchecked until the server
 *     reads them
 * @param ideas the new meals the model suggests, each as a draft idea is
 */
record MenuProposal(
    @JsonPropertyDescription("Up to 6 ids of library meals returned by a tool, best first")
        List<Long> mealIds,
    @JsonPropertyDescription("Up to 3 new meals that are not in the library yet")
        List<DraftProposal.Idea> ideas) {}
