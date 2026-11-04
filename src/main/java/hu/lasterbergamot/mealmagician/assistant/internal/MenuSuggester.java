package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.MenuSuggestions;
import hu.lasterbergamot.mealmagician.assistant.MenuSuggestions.MenuIdea;
import hu.lasterbergamot.mealmagician.assistant.MenuSuggestions.MenuMeal;
import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.planning.MenuItemView;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.OccasionSummary;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Suggests a menu for one occasion (#161, Appendix D.5). It reads the occasion's type, name and
 * date and the menus of up to {@link #PAST_MENUS} earlier occasions of the same type, asks the
 * model with the read-only tools, and checks the answer like a draft: a meal id that is not a live
 * meal of the household is dropped and never shown, and at most six meals and three ideas are kept.
 *
 * <p>Nothing here writes to a menu. The suggestions are shown and a member decides. The occasion is
 * read first, so another household's occasion is a 404 before anything is sent to the model.
 */
@Component
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class MenuSuggester {

  /** The most library meals one menu suggestion returns. */
  static final int MAX_MEALS = 6;

  /** The most new ideas one menu suggestion returns. */
  static final int MAX_IDEAS = 3;

  /** How many earlier occasions of the same type are shown to the model. */
  static final int PAST_MENUS = 5;

  /** How far back the earlier occasions are looked for. */
  static final int YEARS_BACK = 5;

  private final ModelGateway gateway;
  private final PromptBuilder prompts;
  private final MealService meals;
  private final OccasionService occasions;
  private final DraftGenerator tools;
  private final Clock clock;

  /**
   * Creates the suggester.
   *
   * @param gateway the only door to the model
   * @param prompts builds the messages
   * @param meals the catalog's public service, for the live meals and to create an idea
   * @param occasions the planner's occasion service, for the occasion and its earlier menus
   * @param tools the draft generator, which builds the read-only tools for one household
   * @param clock the application clock
   */
  public MenuSuggester(
      ModelGateway gateway,
      PromptBuilder prompts,
      MealService meals,
      OccasionService occasions,
      DraftGenerator tools,
      Clock clock) {
    this.gateway = gateway;
    this.prompts = prompts;
    this.meals = meals;
    this.occasions = occasions;
    this.tools = tools;
    this.clock = clock;
  }

  /**
   * Suggests library meals and ideas for an occasion's menu. Nothing is stored.
   *
   * @param userId the member asking; the tools run as them
   * @param householdId the household
   * @param locale the member's language, in which the reasons are written
   * @param occasionId the occasion
   * @return up to six library meals and three ideas; either list may be empty
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException if the occasion is
   *     not in this household (no model call was made)
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household has
   *     used up its allowance; no model call was made
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model failed
   * @throws DraftGenerationException if the model's answer was not JSON
   */
  public MenuSuggestions suggest(long userId, long householdId, Locale locale, long occasionId) {
    OccasionView occasion = occasions.find(userId, householdId, occasionId);
    Map<Long, String> names = activeNames(userId, householdId);
    Map<String, Object> facts =
        Map.of(
            "name", occasion.name(),
            "type", occasion.type().name(),
            "date", occasion.date().toString());
    var converter = new BeanOutputConverter<>(MenuProposal.class);
    List<Message> messages =
        prompts.buildMenu(
            locale,
            LocalDate.now(clock),
            facts,
            pastMenus(userId, householdId, occasion, names),
            converter.getFormat());
    String reply =
        gateway
            .complete(householdId, new Prompt(messages, tools.toolOptions(userId, householdId)))
            .text();
    MenuProposal proposal = parse(converter, reply);
    if (proposal == null) {
      throw new DraftGenerationException("the answer was not valid JSON");
    }
    return usable(proposal, names);
  }

  /**
   * Adds an idea to the meal library, as the member asked. The meal is not put on any menu; that is
   * a separate step the member takes from the library.
   *
   * @param userId the member adding it
   * @param householdId the household
   * @param occasionId the occasion the idea came from; it must be this household's
   * @param name the idea's name; cleaned and cut to the catalog's limit
   * @param tags the idea's tags
   * @param ingredients the idea's ingredients
   * @return the cleaned name the meal was created with
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException if the occasion is
   *     not in this household
   * @throws ResponseStatusException (400) if the name is blank once cleaned
   */
  public String addIdea(
      long userId,
      long householdId,
      long occasionId,
      String name,
      List<String> tags,
      List<String> ingredients) {
    occasions.find(userId, householdId, occasionId);
    String title = DraftValidator.clean(name, DraftValidator.NAME_MAX);
    if (title.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name");
    }
    meals.create(
        userId,
        householdId,
        new MealDetails(
            title,
            null,
            null,
            DraftValidator.split(
                DraftValidator.names(
                    tags,
                    DraftValidator.TAG_MAX,
                    DraftValidator.TAGS_PER_IDEA,
                    DraftValidator.TAGS_MAX)),
            null,
            DraftValidator.split(
                DraftValidator.names(
                    ingredients,
                    DraftValidator.INGREDIENT_MAX,
                    DraftValidator.INGREDIENTS_PER_MEAL,
                    DraftValidator.INGREDIENTS_MAX))));
    return title;
  }

  private Map<Long, String> activeNames(long userId, long householdId) {
    return meals.search(userId, householdId, new MealSearch("", null, null, false)).stream()
        .collect(Collectors.toMap(MealView::id, MealView::name));
  }

  /** The menus of the latest earlier occasions of the same type, with names the model can use. */
  private List<Map<String, Object>> pastMenus(
      long userId, long householdId, OccasionView occasion, Map<Long, String> names) {
    return occasions
        .between(
            userId,
            householdId,
            occasion.date().minusYears(YEARS_BACK),
            occasion.date().minusDays(1))
        .stream()
        .filter(summary -> summary.type() == occasion.type())
        .sorted(Comparator.comparing(OccasionSummary::date).reversed())
        .limit(PAST_MENUS)
        .map(summary -> occasions.find(userId, householdId, summary.id()))
        .map(past -> pastMenu(past, names))
        .toList();
  }

  private static Map<String, Object> pastMenu(OccasionView past, Map<Long, String> names) {
    List<Map<String, Object>> meals =
        past.menu().stream()
            .filter(item -> names.containsKey(item.mealId()))
            .map(item -> mealLine(item, names.get(item.mealId())))
            .toList();
    return Map.of("name", past.name(), "date", past.date().toString(), "meals", meals);
  }

  private static Map<String, Object> mealLine(MenuItemView item, String name) {
    return Map.of("mealId", item.mealId(), "name", name);
  }

  private static MenuSuggestions usable(MenuProposal proposal, Map<Long, String> names) {
    List<MenuMeal> libraryMeals =
        Optional.ofNullable(proposal.mealIds()).orElse(List.of()).stream()
            .filter(names::containsKey)
            .distinct()
            .limit(MAX_MEALS)
            .map(id -> new MenuMeal(id, names.get(id)))
            .toList();
    List<MenuIdea> ideas =
        Optional.ofNullable(proposal.ideas()).orElse(List.of()).stream()
            .filter(Objects::nonNull)
            .map(MenuSuggester::idea)
            .filter(idea -> !idea.name().isEmpty())
            .limit(MAX_IDEAS)
            .toList();
    return new MenuSuggestions(libraryMeals, ideas);
  }

  private static MenuIdea idea(DraftProposal.Idea idea) {
    return new MenuIdea(
        DraftValidator.clean(idea.name(), DraftValidator.NAME_MAX),
        DraftValidator.clean(idea.reason(), DraftValidator.REASON_MAX),
        DraftValidator.split(
            DraftValidator.names(
                idea.tags(),
                DraftValidator.TAG_MAX,
                DraftValidator.TAGS_PER_IDEA,
                DraftValidator.TAGS_MAX)),
        DraftValidator.split(
            DraftValidator.names(
                idea.ingredients(),
                DraftValidator.INGREDIENT_MAX,
                DraftValidator.INGREDIENTS_PER_MEAL,
                DraftValidator.INGREDIENTS_MAX)));
  }

  private static MenuProposal parse(BeanOutputConverter<MenuProposal> converter, String reply) {
    try {
      return converter.convert(reply);
    } catch (RuntimeException notJson) {
      // The text is not logged or kept in the exception: it may echo the prompt.
      return null;
    }
  }
}
