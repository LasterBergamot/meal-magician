package hu.lasterbergamot.mealmagician.catalog;

import hu.lasterbergamot.mealmagician.catalog.internal.Meal;
import hu.lasterbergamot.mealmagician.catalog.internal.MealRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An <strong>unchecked reader</strong>: it returns meal names without asking who is calling. That
 * breaks the usual rule that every public service method checks household membership, so it is kept
 * as small as possible and an ArchUnit test ({@code UncheckedReadersTest}) lets only the {@code
 * planning} module use it.
 *
 * <p>Why it exists: {@code planning.SharedWeekReader} serves the public share-link page, where
 * nobody is signed in, but still needs the names of the planned meals. {@link MealService} cannot
 * be used for that because it needs a user. The household id still scopes the query, so a meal of
 * another household is never returned even if a wrong id is passed.
 */
@Service
@Transactional(readOnly = true)
public class MealNameLookup {

  private final MealRepository meals;

  /**
   * Creates the lookup.
   *
   * @param meals persistence for meals
   */
  public MealNameLookup(MealRepository meals) {
    this.meals = meals;
  }

  /**
   * Finds meal names, including archived meals (a plan keeps showing a meal that was archived
   * later). Does <em>no</em> membership check: only call it for a household id that came from a
   * trusted source such as a stored share link.
   *
   * @param householdId the household the meals must belong to
   * @param mealIds the meals to look up; may be empty
   * @return the name of each meal found, by meal id; ids that do not exist in this household are
   *     simply missing from the map
   */
  public Map<Long, String> names(long householdId, Collection<Long> mealIds) {
    Map<Long, String> names = new HashMap<>();
    if (mealIds.isEmpty()) {
      return names;
    }
    for (Meal meal : meals.findByHouseholdIdAndIdIn(householdId, mealIds)) {
      names.put(meal.getId(), meal.getName());
    }
    return names;
  }
}
