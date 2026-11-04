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
 * only when the form is submitted. Ingredients are a list from the start: the picker posts one
 * {@code ingredients} parameter per chip, because an ingredient name may contain a comma.
 *
 * @param id the meal being edited, or {@code null} for a new meal
 * @param version the meal's version when the form was opened, sent back to notice a stale form;
 *     {@code 0} for a new meal
 * @param name the typed name
 * @param notes the typed notes
 * @param recipeUrl the typed recipe link
 * @param tags the typed tags, separated by commas
 * @param occasionTypes the ticked occasion types
 * @param ingredients the ingredient names in the picker, one per chip, in the order they were added
 */
public record MealForm(
    Long id,
    long version,
    String name,
    String notes,
    String recipeUrl,
    String tags,
    Set<OccasionType> occasionTypes,
    List<String> ingredients) {

  /**
   * A blank form for a new meal.
   *
   * @return the empty form
   */
  public static MealForm blank() {
    return new MealForm(null, 0, "", "", "", "", EnumSet.noneOf(OccasionType.class), List.of());
  }

  /**
   * A new-meal form that already holds a name, used when a failed quick add falls back to the full
   * form.
   *
   * @param name the name that was typed
   * @return the form with only the name filled in
   */
  public static MealForm named(String name) {
    return new MealForm(null, 0, name, "", "", "", EnumSet.noneOf(OccasionType.class), List.of());
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
        types,
        List.copyOf(meal.ingredients()));
  }

  /**
   * The ingredient names of a request, read from the repeated {@code ingredients} parameter.
   *
   * <p>The controller passes {@code request.getParameterValues("ingredients")} here instead of
   * binding a {@code List<String>} parameter, because Spring splits a single value of such a
   * parameter at commas, and "Salt, coarse" must stay one ingredient.
   *
   * @param values the raw values, or {@code null} when the request has none
   * @return the values in order, as a new list; empty for {@code null}
   */
  public static List<String> namesOf(String[] values) {
    return values == null ? List.of() : List.of(values);
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
   * @return the details, not yet validated; the ingredients go through unchanged and the service
   *     cleans and checks each name
   */
  public MealDetails details() {
    List<String> tagNames =
        Arrays.stream(tags.split(",")).map(String::strip).filter(name -> !name.isEmpty()).toList();
    return new MealDetails(name, notes, recipeUrl, tagNames, occasionTypes, ingredients);
  }
}
