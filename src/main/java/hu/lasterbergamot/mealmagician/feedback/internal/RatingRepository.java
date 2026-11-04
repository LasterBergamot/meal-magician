package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.Thumb;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for ratings. Every method takes the household id, so a rating of another household is
 * never returned (persistence.md).
 */
public interface RatingRepository extends JpaRepository<Rating, Long> {

  /**
   * A member's own rating of a slot, for give and clear.
   *
   * @param slotId the slot (it may no longer exist)
   * @param userId the author
   * @param householdId the household
   * @return the rating, or empty if there is none
   */
  Optional<Rating> findBySlotIdAndUserIdAndHouseholdId(long slotId, long userId, long householdId);

  /**
   * Every member's rating of one slot.
   *
   * @param householdId the household
   * @param slotId the slot
   * @return the ratings, in no particular order
   */
  List<Rating> findByHouseholdIdAndSlotId(long householdId, long slotId);

  /**
   * Which of some slots a member has already rated, for the "how did it go" prompt.
   *
   * @param householdId the household
   * @param userId the member
   * @param slotIds the candidate slots
   * @return the member's ratings of those slots
   */
  List<Rating> findByHouseholdIdAndUserIdAndSlotIdIn(
      long householdId, long userId, Collection<Long> slotIds);

  /**
   * A meal's ratings, newest slot date first.
   *
   * @param householdId the household
   * @param mealId the meal
   * @param page how many to return
   * @return the ratings, newest first
   */
  List<Rating> findByHouseholdIdAndMealIdOrderBySlotDateDescIdDesc(
      long householdId, long mealId, Pageable page);

  /**
   * The household's latest ratings of any meal, most recently given or changed first.
   *
   * @param householdId the household; the only filter that keeps other households out
   * @param page how many to return
   * @return the ratings, newest first
   */
  List<Rating> findByHouseholdIdOrderByUpdatedAtDescIdDesc(long householdId, Pageable page);

  /**
   * Counts a meal's ratings with one thumb.
   *
   * @param householdId the household
   * @param mealId the meal
   * @param thumb up or down
   * @return the count
   */
  long countByHouseholdIdAndMealIdAndThumb(long householdId, long mealId, Thumb thumb);

  /**
   * The mean of the stars given to a meal.
   *
   * @param householdId the household
   * @param mealId the meal
   * @return the mean, or {@code null} when nobody gave stars
   */
  @Query(
      "select avg(r.stars) from Rating r where r.householdId = :householdId"
          + " and r.mealId = :mealId")
  Double averageStars(@Param("householdId") long householdId, @Param("mealId") long mealId);

  /**
   * Deletes a member's rating of a slot.
   *
   * @param slotId the slot
   * @param userId the author
   * @param householdId the household
   * @return how many rows were deleted, 0 or 1
   */
  long deleteBySlotIdAndUserIdAndHouseholdId(long slotId, long userId, long householdId);
}
