package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.internal.MealSlot;
import hu.lasterbergamot.mealmagician.planning.internal.MealSlotRepository;
import hu.lasterbergamot.mealmagician.planning.internal.SlotSeed;
import hu.lasterbergamot.mealmagician.planning.internal.SlotWriter;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The weekly plan's public entry point: reading an ISO week, adding a meal to a day, moving,
 * reordering and removing slots, editing a slot's note, copying a day or a week, and listing
 * recently planned meals. The planner's controllers call only this class.
 *
 * <p>Every method takes the asking person's id and the household id, and first checks membership
 * through {@link HouseholdService#require}, so a stranger gets 404 before any slot is read. A slot
 * of another household gives {@link SlotNotFoundException} (404), and a meal of another household
 * gives {@link hu.lasterbergamot.mealmagician.catalog.MealNotFoundException} (404), because every
 * lookup is scoped by household (PLAN.md D8). Every member may plan: nothing here is owner-only.
 *
 * <p>Changes to an existing slot (move, reorder, note, remove) take the version the caller last
 * saw. An older version gives {@link PlanConflictException} (409) before anything is written; so
 * does a write the database refuses because the slot changed in the meantime.
 *
 * <p>Archiving a meal does not touch the plan. A slot that holds an archived meal keeps it, the
 * week view still shows it and it can still be moved or removed; the library's default list simply
 * no longer offers it. A meal that is still planned cannot be deleted at all: the database refuses,
 * so no plan entry disappears silently. Only {@code SlotPlanned} is published, on add, move and
 * copy (one event per new slot).
 */
@Service
@Transactional
public class PlanningService {

  /** Day, then slot type in the order the enum declares, then place in the group. */
  private static final Comparator<MealSlot> WEEK_ORDER =
      Comparator.comparing(MealSlot::getSlotDate)
          .thenComparing(slot -> slot.getSlotType().ordinal())
          .thenComparingInt(MealSlot::getPosition)
          .thenComparing(MealSlot::getId);

  private final MealSlotRepository slots;
  private final MealService meals;
  private final HouseholdService households;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final SlotWriter writer;

  /**
   * Creates the service.
   *
   * @param slots persistence for planned slots
   * @param meals the catalog's public service, which checks a meal belongs to the household
   * @param households the household service, which does the membership check
   * @param events publishes {@link SlotPlanned}
   * @param clock the application clock, for "today" in the recently-used list
   * @param writer the shared writer that copying a day or a week uses
   */
  public PlanningService(
      MealSlotRepository slots,
      MealService meals,
      HouseholdService households,
      ApplicationEventPublisher events,
      Clock clock,
      SlotWriter writer) {
    this.slots = slots;
    this.meals = meals;
    this.households = households;
    this.events = events;
    this.clock = clock;
    this.writer = writer;
  }

  /**
   * Reads one ISO week of the plan. Any date of the week may be given; the week runs from its
   * Monday to its Sunday.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household whose plan it is
   * @param anyDay any day of the wanted week, not {@code null}
   * @return the week's slots, ordered by date, slot type and position
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public PlanWeek week(long userId, long householdId, LocalDate anyDay) {
    households.require(householdId, userId);
    LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    List<SlotView> found =
        slots.findByHouseholdIdAndSlotDateBetween(householdId, monday, monday.plusDays(6)).stream()
            .sorted(WEEK_ORDER)
            .map(PlanningService::view)
            .toList();
    return new PlanWeek(monday, found);
  }

  /**
   * Reads one planned slot, for the editor that opens on it. The slot's version is the one the
   * editor sends back with every change.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household whose plan it is
   * @param slotId the slot
   * @return the slot as it is now
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws SlotNotFoundException if the slot does not exist in this household
   */
  @Transactional(readOnly = true)
  public SlotView slot(long userId, long householdId, long slotId) {
    households.require(householdId, userId);
    return view(findOwned(householdId, slotId));
  }

  /**
   * Puts a library meal on a day, after the meals already in that date and slot type.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household whose plan it is
   * @param date the day
   * @param slotType the slot type
   * @param mealId the meal, from the household's library (archived meals are accepted too)
   * @param note the note for this slot; blank means none
   * @return the new slot
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException if the meal does not exist
   *     in this household
   * @throws InvalidPlanException with {@code NOTE_TOO_LONG}; nothing is written then
   */
  public SlotView add(
      long userId, long householdId, LocalDate date, SlotType slotType, long mealId, String note) {
    households.require(householdId, userId);
    String cleanNote = PlanRules.note(note);
    meals.find(userId, householdId, mealId);
    List<MealSlot> group = group(householdId, date, slotType);
    MealSlot slot =
        slots.save(new MealSlot(householdId, date, slotType, group.size(), mealId, cleanNote));
    events.publishEvent(new SlotPlanned(householdId, slot.getId(), date, slotType, mealId));
    return view(slot);
  }

  /**
   * Creates a new library meal from its name and plans it in one step, for the pick sheet's "Create
   * and add". Both writes share this transaction, so a rejected name leaves neither a meal nor a
   * slot behind. It publishes {@code MealChanged} (from the catalog) and {@link SlotPlanned}.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household whose plan and library it is
   * @param date the day
   * @param slotType the slot type
   * @param mealName the new meal's name; trimmed and checked by the catalog
   * @return the new slot, without a note
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.catalog.InvalidMealException if the name is blank or too
   *     long; nothing is written then
   */
  public SlotView quickAddAndPlan(
      long userId, long householdId, LocalDate date, SlotType slotType, String mealName) {
    MealView meal = meals.quickAdd(userId, householdId, mealName);
    return add(userId, householdId, date, slotType, meal.id(), null);
  }

  /**
   * Moves a slot to another day and slot type. It joins the end of that group. Moving to the day
   * and type it already has changes nothing and publishes nothing.
   *
   * @param userId the {@code app_user} id of the person moving it
   * @param householdId the household whose plan it is
   * @param slotId the slot
   * @param expectedVersion the version the person last saw
   * @param date the new day
   * @param slotType the new slot type
   * @return the slot after the move
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws SlotNotFoundException if the slot does not exist in this household
   * @throws PlanConflictException if the slot changed since {@code expectedVersion}
   */
  public SlotView move(
      long userId,
      long householdId,
      long slotId,
      long expectedVersion,
      LocalDate date,
      SlotType slotType) {
    households.require(householdId, userId);
    MealSlot slot = findOwned(householdId, slotId);
    checkVersion(slot, expectedVersion);
    if (slot.getSlotDate().equals(date) && slot.getSlotType() == slotType) {
      return view(slot);
    }
    List<MealSlot> from = group(householdId, slot.getSlotDate(), slot.getSlotType());
    List<MealSlot> to = group(householdId, date, slotType);
    from.remove(slot);
    renumber(from);
    slot.moveTo(date, slotType, to.size());
    flush(slot);
    events.publishEvent(
        new SlotPlanned(householdId, slot.getId(), date, slotType, slot.getMealId()));
    return view(slot);
  }

  /**
   * Moves a slot to a new place inside its own date and slot type. The position is clamped to the
   * group: a number below 0 means the first place, one past the end means the last.
   *
   * @param userId the {@code app_user} id of the person reordering
   * @param householdId the household whose plan it is
   * @param slotId the slot
   * @param expectedVersion the version the person last saw
   * @param position the wanted place, counted from 0
   * @return the slot after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws SlotNotFoundException if the slot does not exist in this household
   * @throws PlanConflictException if the slot changed since {@code expectedVersion}
   */
  public SlotView reorder(
      long userId, long householdId, long slotId, long expectedVersion, int position) {
    households.require(householdId, userId);
    MealSlot slot = findOwned(householdId, slotId);
    checkVersion(slot, expectedVersion);
    List<MealSlot> group = group(householdId, slot.getSlotDate(), slot.getSlotType());
    group.remove(slot);
    group.add(Math.clamp(position, 0, group.size()), slot);
    renumber(group);
    flush(slot);
    return view(slot);
  }

  /**
   * Replaces the note of a slot. A blank note clears it.
   *
   * @param userId the {@code app_user} id of the person editing
   * @param householdId the household whose plan it is
   * @param slotId the slot
   * @param expectedVersion the version the person last saw
   * @param note the new note; blank means none
   * @return the slot after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidPlanException with {@code NOTE_TOO_LONG}; nothing is written then
   * @throws SlotNotFoundException if the slot does not exist in this household
   * @throws PlanConflictException if the slot changed since {@code expectedVersion}
   */
  public SlotView editNote(
      long userId, long householdId, long slotId, long expectedVersion, String note) {
    households.require(householdId, userId);
    String cleanNote = PlanRules.note(note);
    MealSlot slot = findOwned(householdId, slotId);
    checkVersion(slot, expectedVersion);
    slot.changeNote(cleanNote);
    flush(slot);
    return view(slot);
  }

  /**
   * Takes a meal out of a day. The slots after it in the same group move up one place, so the
   * positions stay contiguous. Publishes nothing.
   *
   * @param userId the {@code app_user} id of the person removing it
   * @param householdId the household whose plan it is
   * @param slotId the slot
   * @param expectedVersion the version the person last saw
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws SlotNotFoundException if the slot does not exist in this household
   * @throws PlanConflictException if the slot changed since {@code expectedVersion}
   */
  public void remove(long userId, long householdId, long slotId, long expectedVersion) {
    households.require(householdId, userId);
    MealSlot slot = findOwned(householdId, slotId);
    checkVersion(slot, expectedVersion);
    List<MealSlot> group = group(householdId, slot.getSlotDate(), slot.getSlotType());
    group.remove(slot);
    slots.delete(slot);
    renumber(group);
    flush(slot);
  }

  /**
   * Copies every meal of one day onto another day. {@link CopyMode#MERGE} appends the copies after
   * the meals already there; {@link CopyMode#REPLACE} first empties the target day, even slot types
   * the source has nothing in, so the target becomes a copy of the source. Notes are copied with
   * the meals, and archived meals are copied as they are. Publishes one {@link SlotPlanned} per new
   * slot.
   *
   * @param userId the {@code app_user} id of the person copying
   * @param householdId the household whose plan it is
   * @param from the day to copy
   * @param to the day to copy onto
   * @param mode merge or replace
   * @return how many slots were written
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidPlanException with {@code SAME_TARGET} if both days are the same, or {@code
   *     NOTHING_TO_COPY} if the source day is empty (so a replace never silently wipes a day);
   *     nothing is written then
   * @throws PlanConflictException if a replace finds that the target day changed meanwhile
   */
  public int copyDay(long userId, long householdId, LocalDate from, LocalDate to, CopyMode mode) {
    households.require(householdId, userId);
    if (from.equals(to)) {
      throw new InvalidPlanException(PlanProblem.SAME_TARGET);
    }
    List<MealSlot> source = sortedSource(householdId, from, from);
    List<SlotSeed> seeds =
        source.stream()
            .map(slot -> new SlotSeed(to, slot.getSlotType(), slot.getMealId(), slot.getNote()))
            .toList();
    return writer.write(householdId, to, to, seeds, mode);
  }

  /**
   * Copies every meal of one ISO week onto another week, each meal keeping its weekday. Any day of
   * either week may be given. Merge, replace, notes and events work as in {@link #copyDay}; a
   * replace empties Monday to Sunday of the target week.
   *
   * @param userId the {@code app_user} id of the person copying
   * @param householdId the household whose plan it is
   * @param fromAnyDay any day of the week to copy
   * @param toAnyDay any day of the week to copy onto
   * @param mode merge or replace
   * @return how many slots were written
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidPlanException with {@code SAME_TARGET} if both days are in the same week, or
   *     {@code NOTHING_TO_COPY} if the source week is empty; nothing is written then
   * @throws PlanConflictException if a replace finds that the target week changed meanwhile
   */
  public int copyWeek(
      long userId, long householdId, LocalDate fromAnyDay, LocalDate toAnyDay, CopyMode mode) {
    households.require(householdId, userId);
    LocalDate fromMonday = fromAnyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate toMonday = toAnyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    if (fromMonday.equals(toMonday)) {
      throw new InvalidPlanException(PlanProblem.SAME_TARGET);
    }
    List<MealSlot> source = sortedSource(householdId, fromMonday, fromMonday.plusDays(6));
    List<SlotSeed> seeds =
        source.stream()
            .map(slot ->
                new SlotSeed(
                    toMonday.plusDays(ChronoUnit.DAYS.between(fromMonday, slot.getSlotDate())),
                    slot.getSlotType(),
                    slot.getMealId(),
                    slot.getNote()))
            .toList();
    return writer.write(householdId, toMonday, toMonday.plusDays(6), seeds, mode);
  }

  /**
   * Writes an accepted assistant draft into one ISO week of the plan. This is the only door the
   * assistant has to the plan, so it follows the same rules as everything else: {@link
   * CopyMode#MERGE} appends after the meals already there and {@link CopyMode#REPLACE} first
   * empties the whole week, Monday to Sunday (D26), through the shared {@code SlotWriter}. One
   * {@link SlotPlanned} is published per slot, so the listeners see a draft like any other
   * planning. The slots are written without a note, in the order given.
   *
   * <p>Everything is checked before the first write, so a refused draft changes nothing. The whole
   * call runs in one transaction (or joins the caller's), which is why the caller must not hold it
   * open across a model call.
   *
   * @param userId the {@code app_user} id of the person accepting the draft
   * @param householdId the household whose plan it is
   * @param weekMonday the Monday of the week to write; any other day is moved back to its Monday
   * @param draft the slots to write; each date must lie inside that week
   * @param mode merge or replace
   * @return how many slots were written, which is {@code draft.size()}
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidPlanException with {@code NOTHING_TO_COPY} if the list is empty (so a replace
   *     never silently wipes a week), {@code OUTSIDE_WEEK} if a date is not in the week, or {@code
   *     MEAL_ARCHIVED} if a meal is archived; nothing is written then
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException if a meal does not exist
   *     in this household; nothing is written then
   * @throws PlanConflictException if a replace finds that the week changed meanwhile
   */
  public int applyDraft(
      long userId, long householdId, LocalDate weekMonday, List<DraftSlot> draft, CopyMode mode) {
    households.require(householdId, userId);
    if (draft.isEmpty()) {
      throw new InvalidPlanException(PlanProblem.NOTHING_TO_COPY);
    }
    LocalDate monday = weekMonday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate sunday = monday.plusDays(6);
    for (DraftSlot slot : draft) {
      if (slot.date().isBefore(monday) || slot.date().isAfter(sunday)) {
        throw new InvalidPlanException(PlanProblem.OUTSIDE_WEEK);
      }
    }
    // find() is tenant-scoped, so a meal of another household is a 404 here, not a plan entry.
    for (long mealId : draft.stream().map(DraftSlot::mealId).distinct().toList()) {
      if (meals.find(userId, householdId, mealId).archived()) {
        throw new InvalidPlanException(PlanProblem.MEAL_ARCHIVED);
      }
    }
    List<SlotSeed> seeds =
        draft.stream()
            .map(slot -> new SlotSeed(slot.date(), slot.slotType(), slot.mealId(), null))
            .toList();
    return writer.write(householdId, monday, sunday, seeds, mode);
  }

  /**
   * The meals the household planned on or before today, most recent first, for "recently used
   * first" in the library. A meal appears once. The library page sorts its list by this order and
   * keeps the meals not in it at the end.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household whose plan it is
   * @param limit how many ids to return; must be at least 1
   * @return the meal ids, most recently planned first; empty when nothing is planned yet
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws IllegalArgumentException if {@code limit} is below 1
   */
  @Transactional(readOnly = true)
  public List<Long> recentlyPlannedMealIds(long userId, long householdId, int limit) {
    households.require(householdId, userId);
    return slots.findRecentMealIds(householdId, LocalDate.now(clock), PageRequest.of(0, limit));
  }

  /**
   * The slots of a date range in week order, for copying. An empty source is refused here, before
   * anything is written.
   */
  private List<MealSlot> sortedSource(long householdId, LocalDate first, LocalDate last) {
    List<MealSlot> source =
        slots.findByHouseholdIdAndSlotDateBetween(householdId, first, last).stream()
            .sorted(WEEK_ORDER)
            .toList();
    if (source.isEmpty()) {
      throw new InvalidPlanException(PlanProblem.NOTHING_TO_COPY);
    }
    return source;
  }

  /** The tenant-scoped lookup every change goes through. */
  private MealSlot findOwned(long householdId, long slotId) {
    return slots
        .findByIdAndHouseholdId(slotId, householdId)
        .orElseThrow(() -> new SlotNotFoundException(slotId));
  }

  /**
   * Refuses a change made on an old copy of the slot. Checked before any write, so the caller
   * learns about the conflict without a half-done change.
   */
  private static void checkVersion(MealSlot slot, long expectedVersion) {
    if (slot.getVersion() != expectedVersion) {
      throw new PlanConflictException(slot.getId());
    }
  }

  /** The slots of one date and slot type, in place order, read from the database. */
  private List<MealSlot> group(long householdId, LocalDate date, SlotType slotType) {
    return new ArrayList<>(
        slots.findByHouseholdIdAndSlotDateAndSlotTypeOrderByPositionAscIdAsc(
            householdId, date, slotType));
  }

  /** Gives the slots of a group the places 0, 1, 2 ... in their current order. */
  private static void renumber(List<MealSlot> group) {
    for (int place = 0; place < group.size(); place++) {
      group.get(place).reposition(place);
    }
  }

  /**
   * Writes the pending changes now, so that a version clash surfaces inside the service call as
   * {@link PlanConflictException} instead of later, at commit, where the page cannot catch it. The
   * slot is named for the message; for a removal it is the slot that was deleted, which still
   * carries its id.
   */
  private void flush(MealSlot slot) {
    try {
      slots.flush();
    } catch (OptimisticLockingFailureException clash) {
      throw new PlanConflictException(slot.getId());
    }
  }

  private static SlotView view(MealSlot slot) {
    return new SlotView(
        slot.getId(),
        slot.getSlotDate(),
        slot.getSlotType(),
        slot.getPosition(),
        slot.getMealId(),
        slot.getNote(),
        slot.getVersion());
  }
}
