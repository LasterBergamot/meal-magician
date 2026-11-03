package hu.lasterbergamot.mealmagician.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence for ingredients. Ingredients are always looked up within one household. */
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

  /**
   * Finds a household's ingredient by its folded name, which is how two spellings of one ingredient
   * meet.
   *
   * @param householdId the household
   * @param nameKey the accent-free, lower-case name, as {@code MealRules.ingredientKey} produces it
   * @return the ingredient, or empty if the household has none with that name yet
   */
  Optional<Ingredient> findByHouseholdIdAndNameKey(long householdId, String nameKey);

  /**
   * Inserts an ingredient unless the household already has one with the same key. A lost race
   * between two people adding the same new ingredient is therefore not an error and, unlike a
   * failed insert, does not abort a Postgres transaction. The row is not loaded into the
   * persistence context: look it up afterwards.
   *
   * @param householdId the household
   * @param name the name as typed first, already cleaned
   * @param nameKey the folded key of {@code name}
   * @return 1 if the row was inserted, 0 if the household already had it
   */
  @Modifying
  @Query(
      value =
          """
          insert into ingredient (household_id, name, name_key)
          values (:householdId, :name, :nameKey)
          on conflict do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(long householdId, String name, String nameKey);

  /**
   * The ingredients a household actually uses: those on at least one meal.
   *
   * @param householdId the household
   * @return the ingredients ordered by their folded key
   */
  @Query(
      """
      select i from Ingredient i
      where i.householdId = :householdId
        and exists (select mi.id from MealIngredient mi where mi.id.ingredientId = i.id)
      order by i.nameKey
      """)
  List<Ingredient> findInUse(long householdId);

  /**
   * The household's ingredients that match a typed text, for the picker. Those whose key starts
   * with the text come first, then the more used ones (on more meals), then alphabetical.
   *
   * @param householdId the household
   * @param pattern a {@code LIKE} pattern over the key, escaped with {@code !}
   * @param prefixPattern a {@code LIKE} pattern that matches the same text only at the start
   * @param page how many to return
   * @return the best matches first
   */
  @Query(
      """
      select i from Ingredient i
      where i.householdId = :householdId and i.nameKey like :pattern escape '!'
      order by case when i.nameKey like :prefixPattern escape '!' then 0 else 1 end,
               (select count(mi.id.mealId) from MealIngredient mi
                where mi.id.ingredientId = i.id and mi.householdId = :householdId) desc,
               i.nameKey
      """)
  List<Ingredient> suggest(long householdId, String pattern, String prefixPattern, Pageable page);
}
