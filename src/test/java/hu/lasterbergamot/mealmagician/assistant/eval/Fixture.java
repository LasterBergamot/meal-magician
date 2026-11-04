package hu.lasterbergamot.mealmagician.assistant.eval;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The meal library as it is while a case runs, with the real ids the database gave: what the checks
 * compare the model's answer with. It holds archived meals too, because a check must tell "no such
 * meal" from "a meal that is archived".
 *
 * @param meals every meal of the eval household, archived ones included
 */
public record Fixture(List<Meal> meals) {

  /**
   * One meal of the library.
   *
   * @param id the real meal id
   * @param name the meal's name
   * @param tags its tags
   * @param ingredients its ingredients
   * @param occasionTypes the occasion types it suits
   * @param archived whether it is archived (and so must never be proposed)
   */
  public record Meal(
      long id,
      String name,
      List<String> tags,
      List<String> ingredients,
      Set<OccasionType> occasionTypes,
      boolean archived) {}

  /**
   * Looks a meal up.
   *
   * @param id a meal id
   * @return the meal, or empty if the library has no such id
   */
  public Optional<Meal> find(long id) {
    return meals.stream().filter(m -> m.id() == id).findFirst();
  }

  /**
   * Whether an id is a real meal that may be proposed.
   *
   * @param id a meal id
   * @return {@code true} for a meal that exists and is not archived
   */
  public boolean isActive(long id) {
    return find(id).map(m -> !m.archived()).orElse(false);
  }
}
