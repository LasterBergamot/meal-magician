package hu.lasterbergamot.mealmagician.planning;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.internal.Occasion;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionItem;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionItemRepository;
import hu.lasterbergamot.mealmagician.planning.internal.OccasionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occasions: dated events such as a birthday or Christmas, each with its own menu of library meals.
 * Creating, editing, deleting and listing them, the occasions between two days (for the week
 * strip), and the menu: add, quick add, remove and move.
 *
 * <p>Every method takes the asking person's id and the household id, and first checks membership
 * through {@link HouseholdService#require}, so a stranger gets 404 before anything is read. An
 * occasion of another household gives {@link OccasionNotFoundException} (404), and a meal of
 * another household gives {@link hu.lasterbergamot.mealmagician.catalog.MealNotFoundException}
 * (404). Every member may do everything here; nothing is owner-only (PLAN.md D26).
 *
 * <p>Editing or deleting the occasion takes the version the caller last saw; an older version, or a
 * lost race, gives {@link OccasionConflictException} (409). Menu items have no version and no note:
 * adding only appends, and removing or moving an item that is already gone is a 404. Nothing is
 * published.
 */
@Service
@Transactional
public class OccasionService {

  private final OccasionRepository occasions;
  private final OccasionItemRepository items;
  private final MealService meals;
  private final HouseholdService households;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param occasions persistence for occasions
   * @param items persistence for menu items
   * @param meals the catalog's public service, which checks a meal belongs to the household
   * @param households the household service, which does the membership check
   * @param clock the application clock, for the creation time
   */
  public OccasionService(
      OccasionRepository occasions,
      OccasionItemRepository items,
      MealService meals,
      HouseholdService households,
      Clock clock) {
    this.occasions = occasions;
    this.items = items;
    this.meals = meals;
    this.households = households;
    this.clock = clock;
  }

  /**
   * Creates an occasion with an empty menu.
   *
   * @param userId the {@code app_user} id of the person creating it
   * @param householdId the household it joins
   * @param details the name, date, type and notes; the record must not be {@code null}, its fields
   *     may be
   * @return the new occasion
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws InvalidPlanException if a field breaks its rule (see {@link PlanProblem}); nothing is
   *     written then
   */
  public OccasionView create(long userId, long householdId, OccasionDetails details) {
    households.require(householdId, userId);
    OccasionDetails clean = clean(details);
    Occasion occasion =
        occasions.save(
            new Occasion(
                householdId,
                clean.name(),
                clean.date(),
                clean.type(),
                clean.notes(),
                Instant.now(clock)));
    return OccasionViews.occasion(occasion, List.of());
  }

  /**
   * Reads one occasion with its menu.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param occasionId the occasion
   * @return the occasion as it is now
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   */
  @Transactional(readOnly = true)
  public OccasionView find(long userId, long householdId, long occasionId) {
    households.require(householdId, userId);
    return view(findOwned(householdId, occasionId));
  }

  /**
   * Lists all the household's occasions.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @return the occasions by date, then id; empty if there are none
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<OccasionSummary> list(long userId, long householdId) {
    households.require(householdId, userId);
    return summaries(
        householdId, occasions.findByHouseholdIdOrderByOccasionDateAscIdAsc(householdId));
  }

  /**
   * Lists the occasions on the given dates, for the week strip.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param from the first date, inclusive
   * @param to the last date, inclusive
   * @return the occasions by date, then id; empty if there are none
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public List<OccasionSummary> between(
      long userId, long householdId, LocalDate from, LocalDate to) {
    households.require(householdId, userId);
    return summaries(
        householdId,
        occasions.findByHouseholdIdAndOccasionDateBetweenOrderByOccasionDateAscIdAsc(
            householdId, from, to));
  }

  /**
   * Changes an occasion's name, date, type and notes. The menu is not touched.
   *
   * @param userId the {@code app_user} id of the person editing
   * @param householdId the household
   * @param occasionId the occasion
   * @param expectedVersion the version the person last saw
   * @param details the new values
   * @return the occasion after the change
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws OccasionConflictException if the occasion changed since {@code expectedVersion}
   * @throws InvalidPlanException if a field breaks its rule; nothing is written then
   */
  public OccasionView edit(
      long userId,
      long householdId,
      long occasionId,
      long expectedVersion,
      OccasionDetails details) {
    households.require(householdId, userId);
    Occasion occasion = findOwned(householdId, occasionId);
    checkVersion(occasion, expectedVersion);
    OccasionDetails clean = clean(details);
    occasion.change(clean.name(), clean.date(), clean.type(), clean.notes());
    flush(occasion);
    return view(occasion);
  }

  /**
   * Deletes an occasion. Its menu goes with it through {@code ON DELETE CASCADE} in the database.
   * Occasion templates made from it stay.
   *
   * @param userId the {@code app_user} id of the person deleting
   * @param householdId the household
   * @param occasionId the occasion
   * @param expectedVersion the version the person last saw
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws OccasionConflictException if the occasion changed since {@code expectedVersion}
   */
  public void delete(long userId, long householdId, long occasionId, long expectedVersion) {
    households.require(householdId, userId);
    Occasion occasion = findOwned(householdId, occasionId);
    checkVersion(occasion, expectedVersion);
    occasions.delete(occasion);
    flush(occasion);
  }

  /**
   * Adds a library meal to the end of the menu.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household
   * @param occasionId the occasion
   * @param mealId the meal, from the household's library (archived meals are accepted too)
   * @return the occasion with the new menu
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException if the meal does not exist
   *     in this household
   */
  public OccasionView addItem(long userId, long householdId, long occasionId, long mealId) {
    households.require(householdId, userId);
    Occasion occasion = findOwned(householdId, occasionId);
    meals.find(userId, householdId, mealId);
    append(occasion, mealId);
    return view(occasion);
  }

  /**
   * Creates a library meal from its name and adds it to the end of the menu, in one transaction.
   * The occasion is looked up first, so a bad occasion creates no meal. Publishes the catalog's
   * {@code MealChanged}.
   *
   * @param userId the {@code app_user} id of the person adding it
   * @param householdId the household
   * @param occasionId the occasion
   * @param mealName the new meal's name; trimmed and checked by the catalog
   * @return the occasion with the new menu
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws hu.lasterbergamot.mealmagician.catalog.InvalidMealException if the name is blank or too
   *     long; nothing is written then
   */
  public OccasionView quickAddItem(
      long userId, long householdId, long occasionId, String mealName) {
    households.require(householdId, userId);
    Occasion occasion = findOwned(householdId, occasionId);
    MealView meal = meals.quickAdd(userId, householdId, mealName);
    append(occasion, meal.id());
    return view(occasion);
  }

  /**
   * Takes an item off the menu. The items after it move up one place, so the places stay 0, 1, 2
   * and so on.
   *
   * @param userId the {@code app_user} id of the person removing it
   * @param householdId the household
   * @param occasionId the occasion
   * @param itemId the menu item
   * @return the occasion with the new menu
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws MenuItemNotFoundException if the item is not on this occasion (for example already
   *     removed)
   */
  public OccasionView removeItem(long userId, long householdId, long occasionId, long itemId) {
    households.require(householdId, userId);
    Occasion occasion = findOwned(householdId, occasionId);
    OccasionItem item = findItem(householdId, occasionId, itemId);
    List<OccasionItem> menu = menu(householdId, occasionId);
    menu.remove(item);
    items.delete(item);
    renumber(menu);
    return OccasionViews.occasion(occasion, menu);
  }

  /**
   * Moves an item to a new place on the menu. The place is clamped: below 0 means the first place,
   * past the end means the last.
   *
   * @param userId the {@code app_user} id of the person moving it
   * @param householdId the household
   * @param occasionId the occasion
   * @param itemId the menu item
   * @param position the wanted place, counted from 0
   * @return the occasion with the new menu
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws OccasionNotFoundException if the occasion does not exist in this household
   * @throws MenuItemNotFoundException if the item is not on this occasion (for example already
   *     removed)
   */
  public OccasionView moveItem(
      long userId, long householdId, long occasionId, long itemId, int position) {
    households.require(householdId, userId);
    Occasion occasion = findOwned(householdId, occasionId);
    OccasionItem item = findItem(householdId, occasionId, itemId);
    List<OccasionItem> menu = menu(householdId, occasionId);
    menu.remove(item);
    menu.add(Math.clamp(position, 0, menu.size()), item);
    renumber(menu);
    return OccasionViews.occasion(occasion, menu);
  }

  /** Checks the form values in a fixed order and returns the cleaned copy. */
  private OccasionDetails clean(OccasionDetails d) {
    String name = PlanRules.name(d.name());
    if (d.date() == null) {
      throw new InvalidPlanException(PlanProblem.DATE_REQUIRED);
    }
    if (d.type() == null) {
      throw new InvalidPlanException(PlanProblem.TYPE_REQUIRED);
    }
    return new OccasionDetails(name, d.date(), d.type(), PlanRules.notes(d.notes()));
  }

  /** The tenant-scoped lookup every method goes through. */
  private Occasion findOwned(long householdId, long occasionId) {
    return occasions
        .findByIdAndHouseholdId(occasionId, householdId)
        .orElseThrow(() -> new OccasionNotFoundException(occasionId));
  }

  private OccasionItem findItem(long householdId, long occasionId, long itemId) {
    return items
        .findByIdAndOccasionIdAndHouseholdId(itemId, occasionId, householdId)
        .orElseThrow(() -> new MenuItemNotFoundException(itemId));
  }

  /** The menu in order, as a list the caller may change. */
  private List<OccasionItem> menu(long householdId, long occasionId) {
    return new ArrayList<>(
        items.findByOccasionIdAndHouseholdIdOrderByPositionAscIdAsc(occasionId, householdId));
  }

  private void append(Occasion occasion, long mealId) {
    int place = menu(occasion.getHouseholdId(), occasion.getId()).size();
    items.save(new OccasionItem(occasion.getId(), occasion.getHouseholdId(), place, mealId));
  }

  private static void renumber(List<OccasionItem> menu) {
    for (int place = 0; place < menu.size(); place++) {
      menu.get(place).reposition(place);
    }
  }

  private OccasionView view(Occasion occasion) {
    return OccasionViews.occasion(occasion, menu(occasion.getHouseholdId(), occasion.getId()));
  }

  private List<OccasionSummary> summaries(long householdId, List<Occasion> found) {
    Map<Long, Long> counts =
        items.findByHouseholdId(householdId).stream()
            .collect(Collectors.groupingBy(OccasionItem::getOccasionId, Collectors.counting()));
    return found.stream()
        .map(o -> OccasionViews.summary(o, counts.getOrDefault(o.getId(), 0L).intValue()))
        .toList();
  }

  /** Refuses a change made on an old copy of the occasion, before anything is written. */
  private static void checkVersion(Occasion occasion, long expectedVersion) {
    if (occasion.getVersion() != expectedVersion) {
      throw new OccasionConflictException(occasion.getId());
    }
  }

  /**
   * Writes the pending change now, so that a lost race surfaces inside the service call as {@link
   * OccasionConflictException} and not at commit.
   */
  private void flush(Occasion occasion) {
    try {
      occasions.flush();
    } catch (OptimisticLockingFailureException clash) {
      throw new OccasionConflictException(occasion.getId());
    }
  }
}
