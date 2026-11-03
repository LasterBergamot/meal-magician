package hu.lasterbergamot.mealmagician.feedback;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.feedback.internal.Rating;
import hu.lasterbergamot.mealmagician.feedback.internal.RatingRepository;
import hu.lasterbergamot.mealmagician.feedback.internal.RatingWriter;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.household.MemberView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The feedback module's public entry point: giving, changing and clearing a rating, reading the
 * ratings of a slot or a meal, and finding the next meal to ask a member about (D27).
 *
 * <p>Every method takes the asking person's id and the household id and first checks membership
 * (directly through {@link HouseholdService#require}, or through the planning and catalog services
 * it calls), so a stranger gets 404 before anything is read. A slot or meal of another household
 * gives {@code SlotNotFoundException} or {@code MealNotFoundException} (404), because every lookup
 * is scoped by household (PLAN.md D8). Only the author can change or clear a rating: every write is
 * keyed by the caller's own id, so there is no way to name someone else's rating.
 *
 * <p>Every member sees every rating, by display name. E-mail addresses never leave this service. A
 * rating whose author has left the household is not listed, and is not counted in a slot's list,
 * but it stays in the database and in a meal's totals.
 *
 * <p>Ratings outlive their slot: they copy the slot's date, type and meal and have no foreign key
 * to it, so replacing a week (D26) does not delete them.
 */
@Service
@Transactional
public class RatingService {

  private final RatingRepository ratings;
  private final PlanningService planning;
  private final MealService meals;
  private final HouseholdService households;
  private final MemberService members;
  private final ApplicationEventPublisher events;
  private final RatingWriter writer;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param ratings persistence for ratings
   * @param planning the planner's public service, which finds slots and checks membership
   * @param meals the catalog's public service, which checks a meal belongs to the household
   * @param households the household service, which does the membership check
   * @param members the member list, which gives display names
   * @param events publishes {@link RatingWithdrawn}
   * @param writer writes a rating in its own transaction, so {@code give} can retry a lost race
   * @param clock the application clock, for "today" and timestamps
   */
  public RatingService(
      RatingRepository ratings,
      PlanningService planning,
      MealService meals,
      HouseholdService households,
      MemberService members,
      ApplicationEventPublisher events,
      RatingWriter writer,
      Clock clock) {
    this.ratings = ratings;
    this.planning = planning;
    this.meals = meals;
    this.households = households;
    this.members = members;
    this.events = events;
    this.writer = writer;
    this.clock = clock;
  }

  /**
   * Rates a slot, or changes the caller's earlier rating of it. A second give by the same member
   * updates the one row and never adds another. Two tabs giving at once also end with one row: the
   * unique key refuses the second insert, and the write is retried once in a new transaction, which
   * finds the first tab's row and updates it.
   *
   * <p>The method does not start a transaction itself ({@code SUPPORTS}): the retry needs the
   * failed one to be over. It joins one that is already running, e.g. in a test. {@link
   * RatingGiven} is published inside the write's own transaction.
   *
   * @param userId the {@code app_user} id of the person rating
   * @param householdId the household
   * @param slotId the slot being rated
   * @param input the thumb, optional stars and optional comment
   * @return the rating as saved
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException if the slot does not
   *     exist in this household
   * @throws InvalidRatingException with {@code NOT_YET} if the slot is dated after today, or with
   *     the problem {@link RatingRules#check} finds; nothing is written then
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public RatingView give(long userId, long householdId, long slotId, RatingInput input) {
    SlotView slot = planning.slot(userId, householdId, slotId);
    if (slot.date().isAfter(LocalDate.now(clock))) {
      throw new InvalidRatingException(RatingProblem.NOT_YET);
    }
    RatingInput clean = RatingRules.check(input);
    Rating rating;
    try {
      rating = writer.upsert(userId, householdId, slot, clean);
    } catch (DataIntegrityViolationException lostRace) {
      rating = writer.upsert(userId, householdId, slot, clean);
    }
    return view(rating, names(householdId, userId), userId);
  }

  /**
   * Takes the caller's rating of a slot back. It is looked up by slot id, user and household, not
   * through the slot, so it still works after the slot was removed.
   *
   * @param userId the {@code app_user} id of the author
   * @param householdId the household
   * @param slotId the slot (it may no longer exist)
   * @return {@code true} if a rating was removed, {@code false} if there was none
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  public boolean clear(long userId, long householdId, long slotId) {
    households.require(householdId, userId);
    long deleted = ratings.deleteBySlotIdAndUserIdAndHouseholdId(slotId, userId, householdId);
    if (deleted == 0) {
      return false;
    }
    events.publishEvent(new RatingWithdrawn(householdId, slotId, userId));
    return true;
  }

  /**
   * Every current member's rating of one slot, the caller's own flagged with {@code mine}.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param slotId the slot
   * @return the ratings, in the order they were first given
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException if the slot does not
   *     exist in this household
   */
  @Transactional(readOnly = true)
  public List<RatingView> forSlot(long userId, long householdId, long slotId) {
    planning.slot(userId, householdId, slotId);
    List<Rating> found =
        ratings.findByHouseholdIdAndSlotId(householdId, slotId).stream()
            .sorted(Comparator.comparing(Rating::getId))
            .toList();
    return views(found, householdId, userId);
  }

  /**
   * The next meal to ask the caller about: the latest slot in the last days that they have not
   * rated yet. Slots after {@code today} are never offered.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param today the day to count back from (the page passes the clock's today)
   * @param lookbackDays how many days back to look, so 7 covers today and the six days before
   * @return the slot to ask about, or empty when everything in range is rated
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  @Transactional(readOnly = true)
  public Optional<SlotView> nextToRate(
      long userId, long householdId, LocalDate today, int lookbackDays) {
    households.require(householdId, userId);
    LocalDate from = today.minusDays(lookbackDays);
    List<SlotView> candidates = new ArrayList<>();
    // week() returns one ISO week, so a lookback can span two: ask for each Monday in range.
    LocalDate monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    while (!monday.isAfter(today)) {
      planning.week(userId, householdId, monday).slots().stream()
          .filter(slot -> !slot.date().isBefore(from) && !slot.date().isAfter(today))
          .forEach(candidates::add);
      monday = monday.plusWeeks(1);
    }
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    Set<Long> rated =
        ratings
            .findByHouseholdIdAndUserIdAndSlotIdIn(
                householdId, userId, candidates.stream().map(SlotView::id).toList())
            .stream()
            .map(Rating::getSlotId)
            .collect(Collectors.toSet());
    return candidates.stream()
        .filter(slot -> !rated.contains(slot.id()))
        .min(
            Comparator.comparing(SlotView::date)
                .reversed()
                .thenComparing(
                    Comparator.comparingInt((SlotView slot) -> slot.slotType().ordinal())
                        .reversed())
                .thenComparingInt(SlotView::position));
  }

  /**
   * What the household thinks of one meal: thumbs up and down, the mean of the stars and the latest
   * ratings.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param mealId the meal
   * @param limit how many of the latest ratings to list
   * @return the totals and the newest ratings of current members first
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.catalog.MealNotFoundException if the meal does not exist
   *     in this household
   */
  @Transactional(readOnly = true)
  public MealRatings forMeal(long userId, long householdId, long mealId, int limit) {
    meals.find(userId, householdId, mealId);
    List<Rating> latest =
        ratings.findByHouseholdIdAndMealIdOrderBySlotDateDescIdDesc(
            householdId, mealId, PageRequest.of(0, limit));
    return new MealRatings(
        mealId,
        (int) ratings.countByHouseholdIdAndMealIdAndThumb(householdId, mealId, Thumb.UP),
        (int) ratings.countByHouseholdIdAndMealIdAndThumb(householdId, mealId, Thumb.DOWN),
        ratings.averageStars(householdId, mealId),
        views(latest, householdId, userId));
  }

  /** Display names by user id. Only {@code displayName} is read; the e-mail is never copied. */
  private Map<Long, String> names(long householdId, long userId) {
    Map<Long, String> names = new HashMap<>();
    for (MemberView member : members.list(householdId, userId)) {
      names.put(member.userId(), member.displayName());
    }
    return names;
  }

  private List<RatingView> views(List<Rating> found, long householdId, long userId) {
    Map<Long, String> names = names(householdId, userId);
    return found.stream()
        .filter(rating -> names.containsKey(rating.getUserId()))
        .map(rating -> view(rating, names, userId))
        .toList();
  }

  private static RatingView view(Rating rating, Map<Long, String> names, long viewerId) {
    return new RatingView(
        rating.getId(),
        rating.getSlotId(),
        rating.getMealId(),
        rating.getSlotDate(),
        rating.getSlotType(),
        rating.getUserId(),
        names.get(rating.getUserId()),
        rating.getThumb(),
        rating.getStars(),
        rating.getComment(),
        rating.getUserId() == viewerId,
        rating.getUpdatedAt());
  }
}
