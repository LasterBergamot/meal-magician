package hu.lasterbergamot.mealmagician.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One ingredient on one meal. The row also carries the household, which the composite foreign keys
 * in the migration check against the meal and the ingredient, so a link can never cross households.
 */
@Entity
@Table(name = "meal_ingredient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealIngredient {

  @EmbeddedId private MealIngredientKey id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  /**
   * Creates a link between a meal and an ingredient.
   *
   * @param householdId the household both belong to
   * @param mealId the meal
   * @param ingredientId the ingredient
   */
  public MealIngredient(long householdId, long mealId, long ingredientId) {
    this.id = new MealIngredientKey(mealId, ingredientId);
    this.householdId = householdId;
  }
}
