package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Asks the model for a week draft and checks the answer: the one place where a prompt is built,
 * sent and turned into validated items.
 *
 * <p>One call can cover several model requests, because the model calls the read-only {@link
 * AssistantTools} in between. The answer is JSON in the text (Spring AI's {@link
 * BeanOutputConverter} puts the schema in the system prompt and parses the reply), which works on
 * the chosen model together with tools. Every meal id in it is checked against the household's
 * active meals ({@link DraftValidator}). If the model named ids that do not exist, or did not
 * answer with usable JSON, it is told what was wrong and asked <strong>once</strong> more; a second
 * failure ends in {@link DraftGenerationException}, with nothing stored.
 *
 * <p>This class does no database write and holds no transaction: the model call can take many
 * seconds, and a database transaction must not stay open across it. The cap, the timeout and the
 * metering are done by {@link ModelGateway}.
 */
@Component
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class DraftGenerator {

  private final ModelGateway gateway;
  private final PromptBuilder prompts;
  private final MealService meals;
  private final MemberService members;
  private final PlanningService planning;
  private final RatingService ratings;
  private final OccasionService occasions;
  private final EmbeddingIndex index;
  private final Clock clock;

  /**
   * Creates the generator.
   *
   * @param gateway the only door to the model
   * @param prompts builds the messages
   * @param meals the catalog's public service
   * @param members the household's member list, to number the members
   * @param planning the planner's public service, for the tools
   * @param ratings the feedback module's public service, for the tools
   * @param occasions the planner's occasion service, for the tools
   * @param index the meal search, for the tools
   * @param clock the application clock
   */
  public DraftGenerator(
      ModelGateway gateway,
      PromptBuilder prompts,
      MealService meals,
      MemberService members,
      PlanningService planning,
      RatingService ratings,
      OccasionService occasions,
      EmbeddingIndex index,
      Clock clock) {
    this.gateway = gateway;
    this.prompts = prompts;
    this.meals = meals;
    this.members = members;
    this.planning = planning;
    this.ratings = ratings;
    this.occasions = occasions;
    this.index = index;
    this.clock = clock;
  }

  /**
   * A draft the model made and the server accepted.
   *
   * @param result the checked items and summary
   * @param reply the model's raw JSON answer, kept in the conversation
   */
  record Generated(DraftValidator.Result result, String reply) {}

  /**
   * Makes a draft for a week.
   *
   * @param userId the {@code app_user} id of the member who asked; the tools run as them
   * @param householdId the household
   * @param locale the member's language
   * @param monday the Monday of the week to plan
   * @param history the stored turns so far; empty for a new draft
   * @param message the new request or follow-up, at most 1 200 characters
   * @param current the draft as it stands, for a follow-up; {@code null} for a new draft
   * @return the checked draft and the model's answer
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household has
   *     used up its allowance; no model call was made
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model failed
   * @throws DraftGenerationException if the answer was unusable twice in a row
   */
  Generated generate(
      long userId,
      long householdId,
      Locale locale,
      LocalDate monday,
      List<Turn> history,
      String message,
      DraftView current) {
    Set<Long> active =
        meals.search(userId, householdId, new MealSearch("", null, null, false)).stream()
            .map(MealView::id)
            .collect(Collectors.toSet());
    AssistantTools tools =
        new AssistantTools(
            userId,
            householdId,
            Pseudonyms.of(members.list(householdId, userId)),
            planning,
            meals,
            ratings,
            occasions,
            index,
            clock);
    var options = ToolCallingChatOptions.builder().toolCallbacks(ToolCallbacks.from(tools)).build();
    var converter = new BeanOutputConverter<>(DraftProposal.class);
    List<Message> messages =
        prompts.build(
            locale, LocalDate.now(clock), monday, history, message, current, converter.getFormat());

    for (int attempt = 0; ; attempt++) {
      String reply = gateway.complete(householdId, new Prompt(messages, options)).text();
      DraftProposal proposal = parse(converter, reply);
      DraftValidator.Result result =
          proposal == null ? null : DraftValidator.check(proposal, monday, active);
      String complaint =
          result == null
              ? "Your last answer was not valid JSON in the required format. Answer again with"
                  + " only the JSON."
              : result.complaint();
      if (complaint == null) {
        return new Generated(result, reply);
      }
      if (attempt > 0) {
        throw new DraftGenerationException(failure(result));
      }
      messages = new ArrayList<>(messages);
      messages.add(new AssistantMessage(reply));
      messages.add(new UserMessage(complaint));
    }
  }

  private static DraftProposal parse(BeanOutputConverter<DraftProposal> converter, String reply) {
    try {
      return converter.convert(reply);
    } catch (RuntimeException notJson) {
      // The text is not logged or kept in the exception: it may echo the prompt.
      return null;
    }
  }

  private static String failure(DraftValidator.Result result) {
    if (result == null) {
      return "the answer was not valid JSON";
    }
    return result.invalidIds().isEmpty()
        ? "the answer had no usable items"
        : "the answer kept naming meals that are not in the library";
  }
}
