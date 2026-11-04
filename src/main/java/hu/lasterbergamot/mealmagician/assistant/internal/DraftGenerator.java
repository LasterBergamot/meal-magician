package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.assistant.SlotSuggestion;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
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

  /** The most suggestions one slot request returns. */
  static final int MAX_SLOT_SUGGESTIONS = 3;

  private final ModelGateway gateway;
  private final PromptBuilder prompts;
  private final MealService meals;
  private final MemberService members;
  private final PlanningService planning;
  private final RatingService ratings;
  private final OccasionService occasions;
  private final EmbeddingIndex index;
  private final Clock clock;
  private final String chatModel;

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
   * @param chatModel the configured chat model id ({@code spring.ai.google.genai.chat.model}, set
   *     from {@code AI_CHAT_MODEL}); every tool-calling request names it, because the Google client
   *     would otherwise fall back to a model this project has no access to
   * @throws IllegalStateException if {@code chatModel} is blank, so a missing setting stops the
   *     start-up instead of sending a request with no model
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
      Clock clock,
      @Value("${spring.ai.google.genai.chat.model:}") String chatModel) {
    if (chatModel == null || chatModel.isBlank()) {
      throw new IllegalStateException(
          "spring.ai.google.genai.chat.model (AI_CHAT_MODEL) must be set when mm.ai.enabled=true");
    }
    this.chatModel = chatModel;
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
    var options = toolOptions(userId, householdId);
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
      messages = withComplaint(messages, reply, complaint);
    }
  }

  /**
   * Suggests up to three library meals for one slot. Same rules as {@link #generate}: the cap and
   * the timeout are the gateway's, no transaction is open, the model's ids are checked against the
   * household's active meals, and the model is asked once more when its answer was not JSON or
   * named ids that do not exist. After that second try the ids that are real are kept, so an answer
   * that is only partly wrong still helps, and an empty list is a valid result.
   *
   * @param userId the {@code app_user} id of the member who asked; the tools run as them
   * @param householdId the household
   * @param locale the member's language, which the reasons are written in
   * @param date the day of the slot
   * @param slotType the slot type
   * @return at most three suggestions, best first, each a real active meal; may be empty
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household has
   *     used up its allowance; no model call was made
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model failed
   * @throws DraftGenerationException if the answer was not JSON twice in a row
   */
  List<SlotSuggestion> suggestSlot(
      long userId, long householdId, Locale locale, LocalDate date, SlotType slotType) {
    Map<Long, String> names =
        meals.search(userId, householdId, new MealSearch("", null, null, false)).stream()
            .collect(Collectors.toMap(MealView::id, MealView::name));
    var options = toolOptions(userId, householdId);
    var converter = new BeanOutputConverter<>(SlotProposal.class);
    List<Message> messages =
        prompts.buildSlot(
            locale, LocalDate.now(clock), date, slotType.name(), converter.getFormat());

    for (int attempt = 0; ; attempt++) {
      String reply = gateway.complete(householdId, new Prompt(messages, options)).text();
      SlotProposal proposal = parseSlot(converter, reply);
      if (proposal == null && attempt > 0) {
        throw new DraftGenerationException("the answer was not valid JSON");
      }
      if (proposal != null && (attempt > 0 || !hasUnknownIds(proposal, names))) {
        return usable(proposal, names);
      }
      messages =
          withComplaint(
              messages,
              reply,
              proposal == null
                  ? "Your last answer was not valid JSON in the required format. Answer again"
                      + " with only the JSON."
                  : "Some meal ids are not in the library. Use only ids returned by the tools.");
    }
  }

  /**
   * The conversation for the second try: the messages so far, the model's last answer and what was
   * wrong with it.
   *
   * <p>The answer is echoed back only when it has text. Gemini answers an empty message with HTTP
   * 400 ("must include at least one parts field"), and a model can well answer with nothing (#183);
   * then the complaint alone follows the original request, and it says what to do.
   *
   * @param messages the messages sent last time; not changed
   * @param reply the model's answer, possibly empty; never {@code null}
   * @param complaint what was wrong with the answer, never empty
   * @return a new list: the messages, the answer (when it has text) and the complaint
   */
  private static List<Message> withComplaint(
      List<Message> messages, String reply, String complaint) {
    List<Message> next = new ArrayList<>(messages);
    if (!reply.isBlank()) {
      next.add(new AssistantMessage(reply));
    }
    next.add(new UserMessage(complaint));
    return next;
  }

  private static boolean hasUnknownIds(SlotProposal proposal, Map<Long, String> names) {
    return proposal.picks() != null
        && proposal.picks().stream()
            .anyMatch(
                pick -> pick != null && pick.mealId() != null && !names.containsKey(pick.mealId()));
  }

  private static List<SlotSuggestion> usable(SlotProposal proposal, Map<Long, String> names) {
    if (proposal.picks() == null) {
      return List.of();
    }
    Set<Long> seen = new LinkedHashSet<>();
    List<SlotSuggestion> result = new ArrayList<>();
    for (SlotProposal.Pick pick : proposal.picks()) {
      if (pick == null || pick.mealId() == null || !names.containsKey(pick.mealId())) {
        continue;
      }
      if (seen.add(pick.mealId()) && result.size() < MAX_SLOT_SUGGESTIONS) {
        String reason = DraftValidator.clean(pick.reason(), DraftValidator.REASON_MAX);
        result.add(
            new SlotSuggestion(
                pick.mealId(), names.get(pick.mealId()), reason.isEmpty() ? null : reason));
      }
    }
    return result;
  }

  /**
   * The read-only tools, bound to one member and household. The chat and the occasion menu (#161)
   * use the same tools as the week drafts, so they can read the household and nothing else.
   *
   * @param userId the member the tools run as
   * @param householdId the household the tools are bound to
   * @return the Google GenAI chat options carrying the tool callbacks and the configured model id
   *     (typed as the common interface, which is all the callers and the test doubles need)
   */
  ToolCallingChatOptions toolOptions(long userId, long householdId) {
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
    // The Google client casts the options to GoogleGenAiChatOptions, so the generic builder would
    // fail with a ClassCastException. It also needs the model id on every request: without it the
    // SDK falls back to its own default model, which this project has no access to (#183).
    return GoogleGenAiChatOptions.builder()
        .model(chatModel)
        .toolCallbacks(ToolCallbacks.from(tools))
        .build();
  }

  private static SlotProposal parseSlot(BeanOutputConverter<SlotProposal> converter, String reply) {
    try {
      return converter.convert(reply);
    } catch (RuntimeException notJson) {
      // The text is not logged or kept in the exception: it may echo the prompt.
      return null;
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
