package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The values of the create/edit form exactly as the person typed them, so a rejected form (422) is
 * drawn again with nothing lost. Tags are one comma-separated text in the form and become a list
 * only when the form is submitted.
 *
 * @param id the meal being edited, or {@code null} for a new meal
 * @param version the meal's version when the form was opened, sent back to notice a stale form;
 *     {@code 0} for a new meal
 * @param name the typed name
 * @param notes the typed notes
 * @param recipeUrl the typed recipe link
 * @param tags the typed tags, separated by commas
 * @param occasionTypes the ticked occasion types
 */
public record MealForm(
    Long id,
    long version,
    String name,
    String notes,
    String recipeUrl,
    String tags,
    Set<OccasionType> occasionTypes) {

  /**
   * A blank form for a new meal.
   *
   * @return the empty form
   */
  public static MealForm blank() {
    return new MealForm(null, 0, "", "", "", "", EnumSet.noneOf(OccasionType.class));
  }

  /**
   * A new-meal form that already holds a name, used when a failed quick add falls back to the full
   * form.
   *
   * @param name the name that was typed
   * @return the form with only the name filled in
   */
  public static MealForm named(String name) {
    return new MealForm(null, 0, name, "", "", "", EnumSet.noneOf(OccasionType.class));
  }

  /**
   * The form for editing a stored meal.
   *
   * @param meal the meal as the library shows it
   * @return the form filled with its values
   */
  public static MealForm of(MealView meal) {
    Set<OccasionType> types = EnumSet.noneOf(OccasionType.class);
    types.addAll(meal.occasionTypes());
    return new MealForm(
        meal.id(),
        meal.version(),
        meal.name(),
        meal.notes() == null ? "" : meal.notes(),
        meal.recipeUrl() == null ? "" : meal.recipeUrl(),
        String.join(", ", meal.tags()),
        types);
  }

  /**
   * Whether the form edits a stored meal.
   *
   * @return {@code false} for a new meal
   */
  public boolean editing() {
    return id != null;
  }

  /**
   * Where the form posts to: the meal's own address when editing, the list address for a new meal.
   *
   * @return {@code /meals/{id}} or {@code /meals}
   */
  public String action() {
    return editing() ? "/meals/" + id : "/meals";
  }

  /**
   * Where Cancel and the back link go: the meal's page when editing, the list for a new meal.
   *
   * @return {@code /meals/{id}} or {@code /meals}
   */
  public String backHref() {
    return action();
  }

  /**
   * Whether an occasion type is ticked.
   *
   * @param type the type to check
   * @return {@code true} if it is ticked
   */
  public boolean has(OccasionType type) {
    return occasionTypes.contains(type);
  }

  /**
   * The values to hand to {@code MealService}. The tag text is split at commas and blank pieces are
   * dropped, so "a, , b" is two tags; the service then checks each name.
   *
   * @return the details, not yet validated
   */
  public MealDetails details() {
    List<String> tagNames =
        Arrays.stream(tags.split(",")).map(String::strip).filter(name -> !name.isEmpty()).toList();
    return new MealDetails(name, notes, recipeUrl, tagNames, occasionTypes);
  }
}
