package hu.lasterbergamot.mealmagician.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The composite primary key of a {@link MealIngredient}: one meal and one ingredient. JPA needs it
 * as its own type, and it must be {@link Serializable} with equal values for equal links.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealIngredientKey implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "meal_id", nullable = false, updatable = false)
  private long mealId;

  @Column(name = "ingredient_id", nullable = false, updatable = false)
  private long ingredientId;

  /**
   * Creates the key of one link.
   *
   * @param mealId the meal
   * @param ingredientId the ingredient
   */
  public MealIngredientKey(long mealId, long ingredientId) {
    this.mealId = mealId;
    this.ingredientId = ingredientId;
  }
}
