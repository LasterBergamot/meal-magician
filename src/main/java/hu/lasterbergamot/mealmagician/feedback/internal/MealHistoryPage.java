package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.MealRatings;
import hu.lasterbergamot.mealmagician.feedback.RatingView;
import hu.lasterbergamot.mealmagician.feedback.Thumb;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * What the meal page's "How it went" block prints: the thumbs counts, the average stars and the
 * newest ratings, already formatted. The template only prints these values, so it never formats a
 * date or a number itself (the rule for dates in templates).
 *
 * <p>Built by {@link #of}, which turns the {@link MealRatings} of the feedback service into this
 * page model. The formatting happens here, in the reader's language, and not in the template.
 *
 * @param mealId the meal the block is about
 * @param ups how many thumbs up the meal has
 * @param downs how many thumbs down the meal has
 * @param averageLabel the mean of the stars with one decimal in the reader's language (for example
 *     {@code 4,5} in Hungarian), or {@code null} when nobody gave stars
 * @param rows the newest ratings, already in display order; never an e-mail address
 */
public record MealHistoryPage(
    long mealId, int ups, int downs, String averageLabel, List<Row> rows) {

  /**
   * One rating row of the block. It is a copy of the parts the page shows, so a template never sees
   * the feedback module's own types.
   *
   * @param authorName the author's display name (never an e-mail address)
   * @param dateLabel the day of the rated slot, formatted in the reader's language
   * @param thumb up or down
   * @param stars 1 to 5, or {@code null} when the author gave none
   * @param comment the comment, or {@code null} when there is none
   */
  public record Row(
      String authorName, String dateLabel, Thumb thumb, Integer stars, String comment) {}

  /**
   * Builds the page model from the feedback service's answer. Each rating's date is formatted with
   * the medium localised date style of the reader's locale, so a Hungarian reader sees Hungarian
   * month names.
   *
   * @param ratings the meal's totals and newest ratings, as {@code RatingService.forMeal} returns
   *     them
   * @param locale the reader's language, from {@code Messages.locale()}
   * @return the page model; {@code averageLabel} is {@code null} when the meal has no stars and
   *     {@code rows} is empty when no current member has rated it
   */
  public static MealHistoryPage of(MealRatings ratings, Locale locale) {
    String average =
        ratings.averageStars() == null
            ? null
            : String.format(locale, "%.1f", ratings.averageStars());
    List<Row> rows = ratings.latest().stream().map(view -> row(view, locale)).toList();
    return new MealHistoryPage(ratings.mealId(), ratings.ups(), ratings.downs(), average, rows);
  }

  private static Row row(RatingView view, Locale locale) {
    return new Row(
        view.authorName(),
        dateLabel(view.date(), locale),
        view.thumb(),
        view.stars(),
        view.comment());
  }

  private static String dateLabel(LocalDate day, Locale locale) {
    return day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
  }

  /**
   * Tells whether the block has anything to count. A meal with no thumbs at all shows only the
   * empty line; a meal with thumbs shows the counts and the rows.
   *
   * @return {@code true} when at least one thumb was given, by anyone, including former members
   */
  public boolean hasRatings() {
    return ups + downs > 0;
  }

  /**
   * Tells whether the average stars should be printed.
   *
   * @return {@code true} when {@link #averageLabel} is set
   */
  public boolean hasAverage() {
    return averageLabel != null;
  }
}
