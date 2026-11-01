package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.internal.MealSlot;
import hu.lasterbergamot.mealmagician.planning.internal.MealSlotRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
 * reordering and removing slots, editing a slot's note, and listing recently planned meals. The
 * planner's controllers call only this class.
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
 * so no plan entry disappears silently. Only {@code SlotPlanned} is published, and only on add and
 * move.
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

  /**
   * Creates the service.
   *
   * @param slots persistence for planned slots
   * @param meals the catalog's public service, which checks a meal belongs to the household
   * @param households the household service, which does the membership check
   * @param events publishes {@link SlotPlanned}
   * @param clock the application clock, for "today" in the recently-used list
   */
  public PlanningService(
      MealSlotRepository slots,
      MealService meals,
      HouseholdService households,
      ApplicationEventPublisher events,
      Clock clock) {
    this.slots = slots;
    this.meals = meals;
    this.households = households;
    this.events = events;
    this.clock = clock;
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
