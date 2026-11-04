package hu.lasterbergamot.mealmagician.assistant;

import hu.lasterbergamot.mealmagician.assistant.internal.AiProperties;
import hu.lasterbergamot.mealmagician.assistant.internal.ChatService;
import hu.lasterbergamot.mealmagician.assistant.internal.DraftService;
import hu.lasterbergamot.mealmagician.assistant.internal.MenuSuggester;
import hu.lasterbergamot.mealmagician.assistant.internal.UsageLedger;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The public entry point of the assistant module: is the assistant on, how much has a household
 * used this month, and the week drafts (#159): make one, refine it, trim it, add an idea to the
 * library, accept it into the plan or discard it.
 *
 * <p>Drafts belong to the household and any member may use them. Every draft method checks
 * membership first (a stranger gets 404, like for a draft of another household), refuses to work
 * while the assistant is off, and never takes anything from the model on trust. The one-slot
 * suggestions (#160) are here too, and so are the chat (#161) and the occasion menu suggestions
 * (#161). The chat keeps no state here: the caller passes the conversation so far, and the
 * controller stores the new turns in the session.
 */
@Service
public class AssistantService {

  private final AiProperties properties;
  private final UsageLedger ledger;
  private final HouseholdService households;
  private final ObjectProvider<DraftService> drafts;
  private final ObjectProvider<ChatService> chats;
  private final ObjectProvider<MenuSuggester> menus;

  /**
   * Creates the service.
   *
   * @param properties the {@code mm.ai.*} settings
   * @param ledger the spend ledger
   * @param households does the membership check
   * @param drafts the draft logic, which exists only while the assistant is on
   * @param chats the chat logic, which exists only while the assistant is on
   * @param menus the occasion menu logic, which exists only while the assistant is on
   */
  public AssistantService(
      AiProperties properties,
      UsageLedger ledger,
      HouseholdService households,
      ObjectProvider<DraftService> drafts,
      ObjectProvider<ChatService> chats,
      ObjectProvider<MenuSuggester> menus) {
    this.properties = properties;
    this.ledger = ledger;
    this.households = households;
    this.drafts = drafts;
    this.chats = chats;
    this.menus = menus;
  }

  /**
   * Whether the assistant is switched on ({@code mm.ai.enabled}). Pages call this to decide whether
   * to show their entry points at all; it needs no signed-in person and touches no database or
   * network.
   *
   * @return {@code true} when the assistant is on
   */
  public boolean enabled() {
    return properties.enabled();
  }

  /**
   * What a household has used this calendar month (Europe/Budapest), for any member to see.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @return the usage so far; all zeros when nothing has been used yet this month
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   */
  public MonthlyUsage usageThisMonth(long userId, long householdId) {
    if (!enabled()) {
      throw new AssistantDisabledException();
    }
    households.require(householdId, userId);
    return ledger.usageThisMonth(householdId);
  }

  /**
   * Makes a draft for the week that contains a day.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language; the model answers in it
   * @param anyDayInWeek any day of the week to plan
   * @param request what the person asked, at most 1 200 characters
   * @return the stored draft
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws InvalidRequestException if the request is longer than 1 200 characters
   * @throws SpendCapExceededException if the household's monthly allowance is used up
   * @throws ModelUnavailableException if the model failed or timed out
   * @throws DraftGenerationException if the model's answer was unusable, even after one retry
   */
  public DraftView draftWeek(
      long userId, long householdId, Locale locale, LocalDate anyDayInWeek, String request) {
    return draftLogic().draftWeek(userId, householdId, locale, anyDayInWeek, request);
  }

  /**
   * Suggests up to three library meals for one slot. Nothing is stored; planning one of them is the
   * planner's normal "add to slot".
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language; the reasons are written in it
   * @param date the day of the slot
   * @param slotType the slot type
   * @return at most three suggestions, best first; empty when nothing fits
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws SpendCapExceededException if the household's monthly allowance is used up
   * @throws ModelUnavailableException if the model failed or timed out
   * @throws DraftGenerationException if the model's answer was not JSON, even after one retry
   */
  public List<SlotSuggestion> suggestSlot(
      long userId, long householdId, Locale locale, LocalDate date, SlotType slotType) {
    return draftLogic().suggestSlot(userId, householdId, locale, date, slotType);
  }

  /**
   * Changes an open draft after a follow-up message.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language
   * @param draftId the draft
   * @param message the follow-up, at most 1 200 characters
   * @return the draft after the change
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   * @throws DraftClosedException if the draft was accepted
   * @throws InvalidRequestException if the message is longer than 1 200 characters
   * @throws SpendCapExceededException if the household's monthly allowance is used up
   * @throws ModelUnavailableException if the model failed or timed out
   * @throws DraftGenerationException if the model's answer was unusable, even after one retry
   */
  public DraftView refine(
      long userId, long householdId, Locale locale, long draftId, String message) {
    return draftLogic().refine(userId, householdId, locale, draftId, message);
  }

  /**
   * Takes one item out of an open draft.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @param itemId the item
   * @return the draft after the change
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft or the item is not in this household
   * @throws DraftClosedException if the draft was accepted
   */
  public DraftView removeItem(long userId, long householdId, long draftId, long itemId) {
    return draftLogic().removeItem(userId, householdId, draftId, itemId);
  }

  /**
   * Adds an idea of a draft to the meal library, after which the item can be planned.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @param itemId the idea item
   * @return the draft after the change
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household, or the item is not an
   *     unresolved idea of it
   * @throws DraftClosedException if the draft was accepted
   */
  public DraftView addIdeaToLibrary(long userId, long householdId, long draftId, long itemId) {
    return draftLogic().addIdeaToLibrary(userId, householdId, draftId, itemId);
  }

  /**
   * Writes the draft's library meals into the plan through {@code PlanningService.applyDraft} and
   * closes the draft. Ideas still not in the library are left out and counted.
   *
   * @param userId the {@code app_user} id of the person accepting
   * @param householdId the household
   * @param draftId the draft
   * @param mode merge into the week or replace it
   * @return how many slots were written and how many ideas were left out
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   * @throws DraftClosedException if the draft was already accepted (accepting is idempotent: the
   *     second call writes nothing)
   * @throws hu.lasterbergamot.mealmagician.planning.InvalidPlanException if there is no library
   *     meal to write, or one was archived since
   * @throws hu.lasterbergamot.mealmagician.planning.PlanConflictException if a replace lost a race
   */
  public AcceptResult accept(long userId, long householdId, long draftId, CopyMode mode) {
    return draftLogic().accept(userId, householdId, draftId, mode);
  }

  /**
   * Deletes a draft.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   */
  public void discard(long userId, long householdId, long draftId) {
    draftLogic().discard(userId, householdId, draftId);
  }

  /**
   * Reads a draft.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @return the draft with its items
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   */
  public DraftView draft(long userId, long householdId, long draftId) {
    return draftLogic().draft(userId, householdId, draftId);
  }

  /**
   * Answers one message of the chat page (#161). The answer is plain text, written in the member's
   * language, about food and the household's meal plan only (the topic policy of {@code
   * mm.ai.chat-scope}).
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language
   * @param history the conversation so far, oldest first; empty for a new one. The caller keeps it
   *     in the session and it is not stored here
   * @param message the new message, at most 1 000 characters
   * @return the answer's plain text
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws InvalidRequestException if the message is longer than 1 000 characters; no model call
   *     was made
   * @throws SpendCapExceededException if the household's monthly allowance is used up; no model
   *     call was made
   * @throws ModelUnavailableException if the model failed or timed out
   * @throws DraftGenerationException if the model answered with nothing
   */
  public String chat(
      long userId, long householdId, Locale locale, List<ChatTurn> history, String message) {
    ChatService logic = chats.getIfAvailable();
    if (logic == null) {
      throw new AssistantDisabledException();
    }
    return logic.reply(userId, householdId, locale, history, message);
  }

  /**
   * Suggests library meals and new ideas for an occasion's menu (#161, Appendix D.5). Nothing is
   * stored and the menu is not changed; a member adds a suggestion.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language, in which the reasons are written
   * @param occasionId the occasion
   * @return up to six library meals and three ideas
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException if the occasion is
   *     not in this household (a 404, before any model call)
   * @throws SpendCapExceededException if the household's monthly allowance is used up; no model
   *     call was made
   * @throws ModelUnavailableException if the model failed or timed out
   * @throws DraftGenerationException if the model's answer was not JSON
   */
  public MenuSuggestions suggestMenu(
      long userId, long householdId, Locale locale, long occasionId) {
    return menuLogic().suggest(userId, householdId, locale, occasionId);
  }

  /**
   * Adds one idea of an occasion's menu to the meal library (#161). The meal is not put on the
   * menu.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household
   * @param occasionId the occasion the idea came from; it must be this household's
   * @param name the idea's name
   * @param tags the idea's tags
   * @param ingredients the idea's ingredients
   * @return the name the meal was created with, after cleaning
   * @throws AssistantDisabledException if the assistant is switched off
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws hu.lasterbergamot.mealmagician.planning.OccasionNotFoundException if the occasion is
   *     not in this household
   */
  public String addMenuIdea(
      long userId,
      long householdId,
      long occasionId,
      String name,
      List<String> tags,
      List<String> ingredients) {
    return menuLogic().addIdea(userId, householdId, occasionId, name, tags, ingredients);
  }

  /** The occasion menu logic, which only exists while the assistant is on. */
  private MenuSuggester menuLogic() {
    MenuSuggester logic = menus.getIfAvailable();
    if (logic == null) {
      throw new AssistantDisabledException();
    }
    return logic;
  }

  /** The draft logic, which only exists while the assistant is on. */
  private DraftService draftLogic() {
    DraftService logic = drafts.getIfAvailable();
    if (logic == null) {
      throw new AssistantDisabledException();
    }
    return logic;
  }
}
