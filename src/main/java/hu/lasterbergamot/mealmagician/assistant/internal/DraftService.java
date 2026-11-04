package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.assistant.AcceptResult;
import hu.lasterbergamot.mealmagician.assistant.DraftClosedException;
import hu.lasterbergamot.mealmagician.assistant.DraftItemView;
import hu.lasterbergamot.mealmagician.assistant.DraftNotFoundException;
import hu.lasterbergamot.mealmagician.assistant.DraftStatus;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.assistant.InvalidRequestException;
import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.DraftSlot;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The life of a draft: making one with the model, refining it, trimming it, turning an idea into a
 * library meal, and accepting or discarding it. {@code AssistantService} is the public face; this
 * class does the work.
 *
 * <p>Every method first checks that the person belongs to the household, so a draft of another
 * household is a 404 ({@link DraftNotFoundException}) exactly like a draft that does not exist. Any
 * member may use any of the household's drafts.
 *
 * <p><strong>Transactions.</strong> The service has no transaction around the model calls: {@link
 * #draftWeek} and {@link #refine} talk to the model first (it can take many seconds) and then store
 * the result in a short transaction ({@link DraftStore}). Everything that does not call the model
 * is one transaction. {@link #accept} deliberately runs the claim and the plan write together, so a
 * refused write (a week that changed, an archived meal) leaves the draft open.
 */
@Service
@ConditionalOnProperty(prefix = "mm.ai", name = "enabled", havingValue = "true")
public class DraftService {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<List<Turn>> TURNS = new TypeReference<>() {};

  private static final Comparator<DraftItemEntity> ORDER =
      Comparator.comparing(DraftItemEntity::getDayDate)
          .thenComparing(item -> item.getSlotType().ordinal())
          .thenComparingInt(DraftItemEntity::getPosition)
          .thenComparing(DraftItemEntity::getId);

  private final DraftStore store;
  private final DraftRepository drafts;
  private final DraftItemRepository items;
  private final DraftGenerator generator;
  private final MealService meals;
  private final PlanningService planning;
  private final HouseholdService households;

  /**
   * Creates the service.
   *
   * @param store writes a new or refined draft in one short transaction
   * @param drafts persistence for drafts
   * @param items persistence for draft items
   * @param generator asks the model and checks the answer
   * @param meals the catalog's public service
   * @param planning the planner's public service, whose {@code applyDraft} writes the plan
   * @param households does the membership check
   */
  public DraftService(
      DraftStore store,
      DraftRepository drafts,
      DraftItemRepository items,
      DraftGenerator generator,
      MealService meals,
      PlanningService planning,
      HouseholdService households) {
    this.store = store;
    this.drafts = drafts;
    this.items = items;
    this.generator = generator;
    this.meals = meals;
    this.planning = planning;
    this.households = households;
  }

  /**
   * Makes a draft for the week that contains a day. Drafts older than seven days are purged as part
   * of storing the new one.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language, which the model answers in
   * @param anyDayInWeek any day of the week to plan
   * @param request what the person asked, at most 1 200 characters; {@code null} counts as empty
   * @return the stored draft
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws InvalidRequestException if the request is longer than 1 200 characters; no model call
   *     was made
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household's
   *     monthly allowance is used up; no model call was made
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model failed
   * @throws hu.lasterbergamot.mealmagician.assistant.DraftGenerationException if the model's answer
   *     was unusable twice
   */
  public DraftView draftWeek(
      long userId, long householdId, Locale locale, LocalDate anyDayInWeek, String request) {
    households.require(householdId, userId);
    String text = Objects.requireNonNullElse(request, "");
    checkLength(text);
    LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    DraftGenerator.Generated made =
        generator.generate(userId, householdId, locale, monday, List.of(), text, null);
    String conversation = toJson(List.of(Turn.user(text), Turn.assistant(made.reply())));
    DraftEntity saved =
        store.create(
            householdId,
            userId,
            monday,
            text,
            conversation,
            made.result().summary(),
            made.result().items());
    return view(userId, saved);
  }

  /**
   * Changes an open draft after a follow-up ("less meat on Tuesday"). The draft's items are
   * replaced by the model's new proposal, which sees the draft as it stands now, including what
   * members removed by hand.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param locale the person's language
   * @param draftId the draft
   * @param message the follow-up, at most 1 200 characters; {@code null} counts as empty
   * @return the draft after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   * @throws DraftClosedException if the draft was accepted
   * @throws InvalidRequestException if the message is longer than 1 200 characters; no model call
   *     was made
   * @throws hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException if the household's
   *     monthly allowance is used up; no model call was made
   * @throws hu.lasterbergamot.mealmagician.assistant.ModelUnavailableException if the model failed
   * @throws hu.lasterbergamot.mealmagician.assistant.DraftGenerationException if the model's answer
   *     was unusable twice
   */
  public DraftView refine(
      long userId, long householdId, Locale locale, long draftId, String message) {
    households.require(householdId, userId);
    String text = Objects.requireNonNullElse(message, "");
    checkLength(text);
    DraftEntity draft = findOpen(householdId, draftId);
    DraftView current = view(userId, draft);
    List<Turn> history = fromJson(draft.getConversation());
    DraftGenerator.Generated made =
        generator.generate(
            userId, householdId, locale, draft.getWeekStart(), history, text, current);
    List<Turn> turns = new ArrayList<>(history);
    turns.add(Turn.user(text));
    turns.add(Turn.assistant(made.reply()));
    DraftEntity saved =
        store.replace(
            householdId, draftId, toJson(turns), made.result().summary(), made.result().items());
    return view(userId, saved);
  }

  /**
   * Takes one item out of an open draft.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @param itemId the item
   * @return the draft after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft or the item is not in this household
   * @throws DraftClosedException if the draft was accepted
   */
  @Transactional
  public DraftView removeItem(long userId, long householdId, long draftId, long itemId) {
    households.require(householdId, userId);
    DraftEntity draft = findOpen(householdId, draftId);
    if (items.deleteOne(itemId, draftId, householdId) == 0) {
      throw new DraftNotFoundException(draftId, itemId);
    }
    return view(userId, draft);
  }

  /**
   * Creates a library meal from an idea (its name, tags and ingredients) and turns the item into a
   * library item. Only now can the item be planned. Both steps happen in one transaction, so a
   * refused meal leaves the idea as it was.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household
   * @param draftId the draft
   * @param itemId the idea item
   * @return the draft after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household, or the item is not in it
   *     or is not an unresolved idea (already added, for example)
   * @throws DraftClosedException if the draft was accepted
   */
  @Transactional
  public DraftView addIdeaToLibrary(long userId, long householdId, long draftId, long itemId) {
    households.require(householdId, userId);
    DraftEntity draft = findOpen(householdId, draftId);
    DraftItemEntity idea =
        items
            .findByIdAndDraftIdAndHouseholdId(itemId, draftId, householdId)
            .filter(item -> item.getMealId() == null)
            .orElseThrow(() -> new DraftNotFoundException(draftId, itemId));
    MealView meal =
        meals.create(
            userId,
            householdId,
            new MealDetails(
                idea.getIdeaName(),
                null,
                null,
                DraftValidator.split(idea.getIdeaTags()),
                null,
                DraftValidator.split(idea.getIdeaIngredients())));
    idea.resolveTo(meal.id());
    items.saveAndFlush(idea);
    return view(userId, draft);
  }

  /**
   * Writes the draft's library meals into the plan and closes the draft. Ideas that were not added
   * to the library are left out and counted: an idea never reaches the plan by itself.
   *
   * <p>The draft is claimed first with a single conditional update, so of two accepts at the same
   * moment only one writes anything; the other gets {@link DraftClosedException}. The claim and the
   * plan write share one transaction: if the plan refuses (the week changed during a replace, a
   * meal was archived since), the claim is rolled back and the draft stays open.
   *
   * @param userId the {@code app_user} id of the person accepting
   * @param householdId the household
   * @param draftId the draft
   * @param mode merge into the week or replace it (D26)
   * @return how many slots were written and how many ideas were left out
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   * @throws DraftClosedException if the draft was already accepted
   * @throws hu.lasterbergamot.mealmagician.planning.InvalidPlanException if the draft has no
   *     library meal to write, or a meal was archived since
   * @throws hu.lasterbergamot.mealmagician.planning.PlanConflictException if a replace lost a race
   */
  @Transactional
  public AcceptResult accept(long userId, long householdId, long draftId, CopyMode mode) {
    households.require(householdId, userId);
    DraftEntity draft = find(householdId, draftId);
    if (drafts.claim(draftId, householdId) == 0) {
      throw new DraftClosedException(draftId);
    }
    List<DraftItemEntity> all =
        items.findByDraftIdAndHouseholdId(draftId, householdId).stream().sorted(ORDER).toList();
    List<DraftSlot> slots =
        all.stream()
            .filter(item -> item.getMealId() != null)
            .map(item -> new DraftSlot(item.getDayDate(), item.getSlotType(), item.getMealId()))
            .toList();
    int written = planning.applyDraft(userId, householdId, draft.getWeekStart(), slots, mode);
    return new AcceptResult(written, all.size() - slots.size());
  }

  /**
   * Deletes a draft. An accepted draft can be discarded too: its plan entries are not touched.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   */
  @Transactional
  public void discard(long userId, long householdId, long draftId) {
    households.require(householdId, userId);
    if (drafts.deleteOne(draftId, householdId) == 0) {
      throw new DraftNotFoundException(draftId);
    }
  }

  /**
   * Reads a draft.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param draftId the draft
   * @return the draft with its items
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws DraftNotFoundException if the draft is not in this household
   */
  @Transactional(readOnly = true)
  public DraftView draft(long userId, long householdId, long draftId) {
    households.require(householdId, userId);
    return view(userId, find(householdId, draftId));
  }

  private static void checkLength(String text) {
    if (text.length() > InvalidRequestException.MAX_LENGTH) {
      throw new InvalidRequestException(text.length());
    }
  }

  private DraftEntity find(long householdId, long draftId) {
    return drafts
        .findByIdAndHouseholdId(draftId, householdId)
        .orElseThrow(() -> new DraftNotFoundException(draftId));
  }

  private DraftEntity findOpen(long householdId, long draftId) {
    DraftEntity draft = find(householdId, draftId);
    if (draft.getStatus() != DraftStatus.OPEN) {
      throw new DraftClosedException(draftId);
    }
    return draft;
  }

  private DraftView view(long userId, DraftEntity draft) {
    Map<Long, String> names =
        meals.search(userId, draft.getHouseholdId(), new MealSearch("", null, null, true)).stream()
            .collect(Collectors.toMap(MealView::id, MealView::name));
    List<DraftItemView> views =
        items.findByDraftIdAndHouseholdId(draft.getId(), draft.getHouseholdId()).stream()
            .sorted(ORDER)
            .map(item ->
                new DraftItemView(
                    item.getId(),
                    item.getDayDate(),
                    item.getSlotType(),
                    item.getPosition(),
                    item.getMealId(),
                    item.getMealId() == null ? null : names.get(item.getMealId()),
                    item.getIdeaName(),
                    item.getIdeaReason(),
                    DraftValidator.split(item.getIdeaTags()),
                    DraftValidator.split(item.getIdeaIngredients())))
            .toList();
    return new DraftView(
        draft.getId(),
        draft.getWeekStart(),
        draft.getStatus(),
        draft.getRequestText(),
        draft.getSummary(),
        views,
        draft.getCreatedAt());
  }

  private static String toJson(List<Turn> turns) {
    return JSON.writeValueAsString(turns);
  }

  private static List<Turn> fromJson(String json) {
    return JSON.readValue(json, TURNS);
  }
}
