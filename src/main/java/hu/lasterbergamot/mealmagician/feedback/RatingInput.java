package hu.lasterbergamot.mealmagician.feedback;

/**
 * What a member typed into the rating form. Nothing here is trusted: {@link RatingRules#check}
 * validates and cleans it before it reaches the database.
 *
 * @param thumb up or down; required
 * @param stars 1 to 5, or {@code null} for no stars
 * @param comment free text of at most {@link RatingRules#MAX_COMMENT} characters, or {@code null}
 *     or blank for none
 */
public record RatingInput(Thumb thumb, Integer stars, String comment) {}
