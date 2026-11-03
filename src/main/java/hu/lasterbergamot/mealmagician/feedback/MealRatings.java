package hu.lasterbergamot.mealmagician.feedback;

import java.util.List;

/**
 * Everything the household thinks of one meal: the totals and the latest ratings. The totals count
 * every rating of the meal, including those of people who have since left the household; the {@code
 * latest} list shows only current members, by name.
 *
 * @param mealId the meal
 * @param ups how many thumbs up it has
 * @param downs how many thumbs down it has
 * @param averageStars the mean of the stars that were given, or {@code null} when nobody gave any
 * @param latest the newest ratings first (by slot date), at most the limit that was asked for
 */
public record MealRatings(
    long mealId, int ups, int downs, Double averageStars, List<RatingView> latest) {}
