package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One occasion type a meal suits. The row also carries the household, which the composite foreign
 * key in migration V8 checks against the meal, so a link can never cross households.
 */
@Entity
@Table(name = "meal_occasion_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealOccasionType {

  @EmbeddedId private MealOccasionTypeKey id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  /**
   * Creates a link between a meal and an occasion type.
   *
   * @param householdId the household the meal belongs to
   * @param mealId the meal
   * @param occasionType the occasion type it suits
   */
  public MealOccasionType(long householdId, long mealId, OccasionType occasionType) {
    this.id = new MealOccasionTypeKey(mealId, occasionType);
    this.householdId = householdId;
  }
}
