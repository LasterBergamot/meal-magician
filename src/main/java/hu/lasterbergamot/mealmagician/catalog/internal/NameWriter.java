package hu.lasterbergamot.mealmagician.catalog.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds or creates a household's tag or ingredient by its key, safe against two people creating the
 * same new name at the same moment.
 *
 * <p>The naive way (look it up, then save if it is missing) loses the race: both people see
 * "missing", the second insert hits the unique key on {@code (household_id, name_key)}, and on
 * Postgres that failed statement aborts the whole transaction, so the person gets a server error.
 * This class inserts with {@code ON CONFLICT DO NOTHING} instead: the loser's insert waits for the
 * winner's transaction, then does nothing without raising an error, and the lookup that follows
 * finds the winner's row. The loser's transaction stays healthy, so the meal is saved normally.
 *
 * <p>Why not "insert in a new transaction and retry on a constraint error", as the rating writer
 * does? A new transaction would not see a household created earlier in the caller's still-open
 * transaction (as every transactional test does), so the insert would fail on its foreign key. This
 * way the write joins the caller's transaction, and the row is as atomic with the meal as before: a
 * rolled-back meal leaves no stray tag or ingredient.
 *
 * <p>It is a separate bean, with the writes in one place for tags and ingredients, so the service
 * stays free of the race handling and both kinds of names get the same fix.
 */
@Component
public class NameWriter {

  private final TagRepository tags;
  private final IngredientRepository ingredients;

  /**
   * Creates the writer.
   *
   * @param tags persistence for tags
   * @param ingredients persistence for ingredients
   */
  public NameWriter(TagRepository tags, IngredientRepository ingredients) {
    this.tags = tags;
    this.ingredients = ingredients;
  }

  /**
   * Returns the household's tag with this key, creating it first if it does not exist. Joins the
   * caller's transaction.
   *
   * @param householdId the household that owns the tag
   * @param key the lower-case key, as {@code MealRules.tagKey} produces it
   * @param display the name as typed, used only when the tag is created
   * @return the existing or new tag; never {@code null}
   */
  @Transactional
  public Tag tag(long householdId, String key, String display) {
    return tags.findByHouseholdIdAndNameKey(householdId, key)
        .orElseGet(() -> {
          tags.insertIfAbsent(householdId, display, key);
          return tags.findByHouseholdIdAndNameKey(householdId, key).orElseThrow();
        });
  }

  /**
   * Returns the household's ingredient with this key, creating it first if it does not exist. Joins
   * the caller's transaction.
   *
   * @param householdId the household that owns the ingredient
   * @param key the accent-free, lower-case key, as {@code MealRules.ingredientKey} produces it
   * @param display the name as typed, used only when the ingredient is created
   * @return the existing or new ingredient; never {@code null}
   */
  @Transactional
  public Ingredient ingredient(long householdId, String key, String display) {
    return ingredients
        .findByHouseholdIdAndNameKey(householdId, key)
        .orElseGet(() -> {
          ingredients.insertIfAbsent(householdId, display, key);
          return ingredients.findByHouseholdIdAndNameKey(householdId, key).orElseThrow();
        });
  }
}
