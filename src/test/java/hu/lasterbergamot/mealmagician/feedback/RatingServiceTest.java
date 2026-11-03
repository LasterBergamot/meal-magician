package hu.lasterbergamot.mealmagician.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.MealNotFoundException;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotNotFoundException;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ratings against the dev profile's H2 so the mappings meet the real V10 schema. Each test runs in
 * a transaction that is rolled back, so the tests do not see each other's rows.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@RecordApplicationEvents
class RatingServiceTest {

  /** A Tuesday that is not later than the real "today", so its slots can be rated. */
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

  @Autowired private RatingService ratings;
  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @Autowired private ApplicationEvents events;
  @Autowired private Clock clock;

  private long anna;
  private long bela;
  private long cili;
  private long annasHome;
  private long belasHome;
  private long stew;

  @BeforeEach
  void setUp() {
    anna = HouseholdTestSupport.insertUser(jdbc, "rt-anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "rt-bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "rt-cili");
    annasHome = households.create(anna, "Annas").id();
    belasHome = households.create(bela, "Belas").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at)"
            + " values (?, ?, 'MEMBER', now())",
        annasHome,
        cili);
    stew = meals.quickAdd(anna, annasHome, "Stew").id();
  }

  private SlotView plan(LocalDate date, SlotType type) {
    return planning.add(anna, annasHome, date, type, stew, null);
  }

  private RatingInput up() {
    return new RatingInput(Thumb.UP, null, null);
  }

  private int ratingRows() {
    entityManager.flush();
    return jdbc.queryForObject("select count(*) from rating", Integer.class);
  }

  @Test
  void givingOnAPastSlotSavesOneRatingAndPublishesRatingGivenOnce() {
    SlotView slot = plan(LocalDate.now(clock).minusDays(2), SlotType.DINNER);

    RatingView view =
        ratings.give(anna, annasHome, slot.id(), new RatingInput(Thumb.UP, 4, "good"));

    assertThat(ratingRows()).isEqualTo(1);
    assertThat(view.slotId()).isEqualTo(slot.id());
    assertThat(view.mealId()).isEqualTo(stew);
    assertThat(view.date()).isEqualTo(slot.date());
    assertThat(view.slotType()).isEqualTo(SlotType.DINNER);
    assertThat(view.userId()).isEqualTo(anna);
    assertThat(view.authorName()).isEqualTo("rt-anna");
    assertThat(view.stars()).isEqualTo(4);
    assertThat(view.comment()).isEqualTo("good");
    assertThat(view.mine()).isTrue();
    assertThat(view.updatedAt()).isNotNull();
    assertThat(events.stream(RatingGiven.class))
        .containsExactly(
            new RatingGiven(annasHome, view.id(), slot.id(), stew, anna, slot.date(), Thumb.UP, 4));
  }

  @Test
  void givingTwiceUpdatesTheSameRowAndPublishesASecondEvent() {
    SlotView slot = plan(LocalDate.now(clock).minusDays(1), SlotType.LUNCH);
    RatingView first = ratings.give(anna, annasHome, slot.id(), up());

    RatingView second =
        ratings.give(anna, annasHome, slot.id(), new RatingInput(Thumb.DOWN, 4, null));

    assertThat(ratingRows()).isEqualTo(1);
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.thumb()).isEqualTo(Thumb.DOWN);
    assertThat(second.stars()).isEqualTo(4);
    assertThat(ratings.forSlot(anna, annasHome, slot.id()))
        .singleElement()
        .satisfies(stored -> {
          assertThat(stored.thumb()).isEqualTo(Thumb.DOWN);
          assertThat(stored.stars()).isEqualTo(4);
        });
    assertThat(events.stream(RatingGiven.class)).hasSize(2);
  }

  @Test
  void givingAgainRefreshesTheCopiedSlotAfterTheSlotMoved() {
    SlotView slot = plan(LocalDate.now(clock).minusDays(3), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());
    LocalDate newDate = LocalDate.now(clock).minusDays(1);
    planning.move(anna, annasHome, slot.id(), slot.version(), newDate, SlotType.DINNER);

    RatingView again = ratings.give(anna, annasHome, slot.id(), up());

    assertThat(again.date()).isEqualTo(newDate);
    assertThat(again.slotType()).isEqualTo(SlotType.DINNER);
  }

  @Test
  void aSlotOfTodayCanBeRated() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.BREAKFAST);

    ratings.give(anna, annasHome, slot.id(), up());

    assertThat(ratingRows()).isEqualTo(1);
  }

  @Test
  void aSlotOfTomorrowIsRefusedAsNotYet() {
    SlotView slot = plan(LocalDate.now(clock).plusDays(1), SlotType.BREAKFAST);

    assertThatThrownBy(() -> ratings.give(anna, annasHome, slot.id(), up()))
        .isInstanceOfSatisfying(
            InvalidRatingException.class,
            e -> assertThat(e.problem()).isEqualTo(RatingProblem.NOT_YET));
    assertThat(ratingRows()).isZero();
    assertThat(events.stream(RatingGiven.class)).isEmpty();
  }

  @Test
  void aBadInputIsRefusedAndNothingIsSaved() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);

    assertProblem(slot, new RatingInput(null, null, null), RatingProblem.THUMB_REQUIRED);
    assertProblem(slot, new RatingInput(Thumb.UP, 0, null), RatingProblem.STARS_OUT_OF_RANGE);
    assertProblem(slot, new RatingInput(Thumb.UP, 6, null), RatingProblem.STARS_OUT_OF_RANGE);
    assertProblem(
        slot, new RatingInput(Thumb.UP, null, "x".repeat(501)), RatingProblem.COMMENT_TOO_LONG);
    assertThat(ratingRows()).isZero();
  }

  private void assertProblem(SlotView slot, RatingInput input, RatingProblem problem) {
    assertThatThrownBy(() -> ratings.give(anna, annasHome, slot.id(), input))
        .isInstanceOfSatisfying(
            InvalidRatingException.class, e -> assertThat(e.problem()).isEqualTo(problem));
  }

  @Test
  void aBlankCommentIsStoredAsNull() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);

    ratings.give(anna, annasHome, slot.id(), new RatingInput(Thumb.UP, null, "   "));

    assertThat(jdbc.queryForObject("select comment_text from rating", String.class)).isNull();
  }

  @Test
  void aSlotOfAnotherHouseholdIsNotFoundAndNothingIsSaved() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);

    assertThatThrownBy(() -> ratings.give(bela, belasHome, slot.id(), up()))
        .isInstanceOf(SlotNotFoundException.class);
    assertThat(ratingRows()).isZero();
  }

  @Test
  void aPersonInNoHouseholdGetsHouseholdNotFound() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);

    assertThatThrownBy(() -> ratings.give(bela, annasHome, slot.id(), up()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> ratings.clear(bela, annasHome, slot.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> ratings.forSlot(bela, annasHome, slot.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> ratings.forMeal(bela, annasHome, stew, 5))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> ratings.nextToRate(bela, annasHome, TODAY, 7))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void clearingOwnRatingDeletesItAndPublishesRatingWithdrawn() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());

    boolean cleared = ratings.clear(anna, annasHome, slot.id());

    assertThat(cleared).isTrue();
    assertThat(ratingRows()).isZero();
    assertThat(events.stream(RatingWithdrawn.class))
        .containsExactly(new RatingWithdrawn(annasHome, slot.id(), anna));
  }

  @Test
  void clearingNothingReturnsFalseAndPublishesNothing() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);

    assertThat(ratings.clear(anna, annasHome, slot.id())).isFalse();
    assertThat(events.stream(RatingWithdrawn.class)).isEmpty();
  }

  @Test
  void clearingNeverRemovesAnotherMembersRating() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());

    assertThat(ratings.clear(cili, annasHome, slot.id())).isFalse();
    assertThat(ratingRows()).isEqualTo(1);
  }

  @Test
  void clearingStillWorksAfterTheSlotWasRemoved() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());
    planning.remove(anna, annasHome, slot.id(), slot.version());

    assertThat(ratings.clear(anna, annasHome, slot.id())).isTrue();
    assertThat(ratingRows()).isZero();
  }

  @Test
  void aRatingSurvivesTheRemovalOfItsSlotAndStaysOnTheMeal() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());
    planning.remove(anna, annasHome, slot.id(), slot.version());

    MealRatings forMeal = ratings.forMeal(anna, annasHome, stew, 10);

    assertThat(forMeal.latest())
        .singleElement()
        .satisfies(r -> assertThat(r.slotId()).isEqualTo(slot.id()));
    assertThat(forMeal.ups()).isEqualTo(1);
  }

  @Test
  void forSlotShowsEveryMembersRatingWithMineSetAndNoEmail() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());
    ratings.give(cili, annasHome, slot.id(), new RatingInput(Thumb.DOWN, 2, "dry"));

    var seenByCili = ratings.forSlot(cili, annasHome, slot.id());

    assertThat(seenByCili).extracting(RatingView::authorName).containsExactly("rt-anna", "rt-cili");
    assertThat(seenByCili).extracting(RatingView::mine).containsExactly(false, true);
    assertThat(seenByCili.toString()).doesNotContain("@example.com");
  }

  @Test
  void forSlotLeavesOutRatingsOfPeopleWhoLeftTheHousehold() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);
    ratings.give(anna, annasHome, slot.id(), up());
    ratings.give(cili, annasHome, slot.id(), up());
    jdbc.update("delete from membership where user_id = ?", cili);

    assertThat(ratings.forSlot(anna, annasHome, slot.id()))
        .extracting(RatingView::authorName)
        .containsExactly("rt-anna");
  }

  @Test
  void forSlotOfAnotherHouseholdIsNotFound() {
    SlotView slot = plan(LocalDate.now(clock), SlotType.LUNCH);

    assertThatThrownBy(() -> ratings.forSlot(bela, belasHome, slot.id()))
        .isInstanceOf(SlotNotFoundException.class);
  }

  @Test
  void forMealCountsThumbsAveragesStarsAndListsNewestFirst() {
    SlotView older = plan(LocalDate.now(clock).minusDays(3), SlotType.LUNCH);
    SlotView newer = plan(LocalDate.now(clock).minusDays(1), SlotType.LUNCH);
    ratings.give(anna, annasHome, older.id(), new RatingInput(Thumb.UP, 5, null));
    ratings.give(cili, annasHome, older.id(), new RatingInput(Thumb.DOWN, 2, null));
    ratings.give(anna, annasHome, newer.id(), up());

    MealRatings result = ratings.forMeal(anna, annasHome, stew, 2);

    assertThat(result.mealId()).isEqualTo(stew);
    assertThat(result.ups()).isEqualTo(2);
    assertThat(result.downs()).isEqualTo(1);
    assertThat(result.averageStars()).isEqualTo(3.5);
    assertThat(result.latest()).hasSize(2);
    assertThat(result.latest().get(0).slotId()).isEqualTo(newer.id());
  }

  @Test
  void forMealWithoutRatingsHasNoAverage() {
    MealRatings result = ratings.forMeal(anna, annasHome, stew, 5);

    assertThat(result.ups()).isZero();
    assertThat(result.downs()).isZero();
    assertThat(result.averageStars()).isNull();
    assertThat(result.latest()).isEmpty();
  }

  @Test
  void forMealOfAnotherHouseholdIsNotFound() {
    assertThatThrownBy(() -> ratings.forMeal(bela, belasHome, stew, 5))
        .isInstanceOf(MealNotFoundException.class);
  }

  @Test
  void nextToRateReturnsTheLatestUnratedSlotWithinSevenDays() {
    plan(LocalDate.of(2026, 10, 27), SlotType.DINNER);
    plan(LocalDate.of(2026, 11, 2), SlotType.BREAKFAST);
    SlotView latest = plan(LocalDate.of(2026, 11, 2), SlotType.DINNER);
    plan(LocalDate.of(2026, 11, 2), SlotType.LUNCH);

    Optional<SlotView> next = ratings.nextToRate(anna, annasHome, TODAY, 7);

    assertThat(next).map(SlotView::id).contains(latest.id());
  }

  @Test
  void nextToRateOrdersSlotsOfTheSameTypeByPosition() {
    SlotView first = plan(LocalDate.of(2026, 11, 2), SlotType.DINNER);
    plan(LocalDate.of(2026, 11, 2), SlotType.DINNER);

    assertThat(ratings.nextToRate(anna, annasHome, TODAY, 7))
        .map(SlotView::id)
        .contains(first.id());
  }

  @Test
  void nextToRateSkipsASlotTheCallerAlreadyRated() {
    SlotView dinner = plan(LocalDate.of(2026, 11, 2), SlotType.DINNER);
    SlotView lunch = plan(LocalDate.of(2026, 11, 2), SlotType.LUNCH);
    ratings.give(anna, annasHome, dinner.id(), up());

    assertThat(ratings.nextToRate(anna, annasHome, TODAY, 7))
        .map(SlotView::id)
        .contains(lunch.id());
    assertThat(ratings.nextToRate(cili, annasHome, TODAY, 7))
        .map(SlotView::id)
        .contains(dinner.id());
  }

  @Test
  void nextToRateSkipsFutureSlotsAndSlotsOlderThanTheLookback() {
    plan(LocalDate.of(2026, 10, 26), SlotType.DINNER);
    plan(LocalDate.of(2026, 11, 4), SlotType.DINNER);

    assertThat(ratings.nextToRate(anna, annasHome, TODAY, 7)).isEmpty();
  }

  @Test
  void nextToRateIsEmptyWhenEverythingInRangeIsRated() {
    SlotView only = plan(LocalDate.of(2026, 11, 3), SlotType.LUNCH);
    ratings.give(anna, annasHome, only.id(), up());

    assertThat(ratings.nextToRate(anna, annasHome, TODAY, 7)).isEmpty();
  }

  @Test
  void nextToRateIsEmptyForAnEmptyPlan() {
    assertThat(ratings.nextToRate(anna, annasHome, TODAY, 7)).isEmpty();
  }
}
