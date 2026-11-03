package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.feedback.RatingView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link RatingPage} and the {@link PromptPage} in the reader's language. The slot, the
 * meal and the ratings all come through public services that check household membership, so a
 * foreign slot is a 404 here and not a leak.
 *
 * <p>The day label is formatted here and not with {@code planning}'s own helper, because that one
 * is internal to {@code planning} (and {@code feedback} may only use its public API).
 */
@Component
class RatingPageFactory {

  /** How many days back the plan page looks for a meal to ask about (D27). */
  static final int LOOKBACK_DAYS = 7;

  private final RatingService ratings;
  private final PlanningService planning;
  private final MealService meals;
  private final Messages messages;

  RatingPageFactory(
      RatingService ratings, PlanningService planning, MealService meals, Messages messages) {
    this.ratings = ratings;
    this.planning = planning;
    this.meals = meals;
    this.messages = messages;
  }

  /**
   * The form as stored: the member's rating pre-filled, if they gave one, and the others' below.
   *
   * @param userId the signed-in person's id
   * @param householdId the household of the request
   * @param slotId the slot to rate
   * @return the page, with no error and no thanks line
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException if the slot is not in
   *     this household
   */
  RatingPage page(long userId, long householdId, long slotId) {
    return build(userId, householdId, slotId, null, null, false);
  }

  /**
   * The form after a successful save: the saved values and the thanks line.
   *
   * @param userId the signed-in person's id
   * @param householdId the household of the request
   * @param slotId the slot that was rated
   * @return the page with {@code saved} set
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException if the slot is not in
   *     this household
   */
  RatingPage saved(long userId, long householdId, long slotId) {
    return build(userId, householdId, slotId, null, null, true);
  }

  /**
   * The form after a refused post: what the person typed and the message.
   *
   * @param userId the signed-in person's id
   * @param householdId the household of the request
   * @param slotId the slot that was being rated
   * @param typed the values as posted
   * @param errorKey the message key of the refusal
   * @return the page with the typed values and the error
   * @throws hu.lasterbergamot.mealmagician.planning.SlotNotFoundException if the slot is not in
   *     this household
   */
  RatingPage rejected(
      long userId, long householdId, long slotId, RatingForm typed, String errorKey) {
    return build(userId, householdId, slotId, typed, errorKey, false);
  }

  /**
   * The card for the latest meal of the last {@value #LOOKBACK_DAYS} days that the member has not
   * rated.
   *
   * @param userId the signed-in person's id
   * @param householdId the household of the request
   * @param today the day to count back from
   * @return the card, or empty when everything in range is rated
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member of the household
   */
  Optional<PromptPage> prompt(long userId, long householdId, LocalDate today) {
    return ratings
        .nextToRate(userId, householdId, today, LOOKBACK_DAYS)
        .map(slot ->
            new PromptPage(
                slot.id(),
                meals.find(userId, householdId, slot.mealId()).name(),
                dayLabel(slot.date()),
                slot.slotType().name(),
                slotKey(slot.slotType())));
  }

  private RatingPage build(
      long userId,
      long householdId,
      long slotId,
      RatingForm typed,
      String errorKey,
      boolean saved) {
    SlotView slot = planning.slot(userId, householdId, slotId);
    String mealName = meals.find(userId, householdId, slot.mealId()).name();
    List<RatingView> all = ratings.forSlot(userId, householdId, slotId);
    Optional<RatingView> mine = all.stream().filter(RatingView::mine).findFirst();
    RatingForm form = typed != null ? typed : mine.map(RatingForm::of).orElseGet(RatingForm::empty);
    return new RatingPage(
        slot.id(),
        slot.date(),
        mealName,
        dayLabel(slot.date()),
        slot.slotType().name(),
        slotKey(slot.slotType()),
        form,
        mine.isPresent(),
        all.stream().filter(rating -> !rating.mine()).toList(),
        errorKey,
        saved);
  }

  private static String slotKey(SlotType type) {
    return "slot." + type.name().toLowerCase(Locale.ROOT);
  }

  private String dayLabel(LocalDate day) {
    Locale locale = messages.locale();
    String name = day.format(DateTimeFormatter.ofPattern("EEEE", locale));
    String capitalised = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    String date = day.format(DateTimeFormatter.ofLocalizedPattern("MMMd").withLocale(locale));
    return messages.t("plan.day", capitalised, date);
  }
}
