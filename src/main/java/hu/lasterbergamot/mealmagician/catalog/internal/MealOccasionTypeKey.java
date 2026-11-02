package hu.lasterbergamot.mealmagician.catalog.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The composite primary key of a {@link MealOccasionType}: one meal and one occasion type. JPA
 * needs it as its own type, and it must be {@link Serializable} with equal values for equal links.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealOccasionTypeKey implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "meal_id", nullable = false, updatable = false)
  private long mealId;

  @Enumerated(EnumType.STRING)
  @Column(name = "occasion_type", nullable = false, updatable = false, length = 10)
  private OccasionType occasionType;

  /**
   * Creates the key of one link.
   *
   * @param mealId the meal
   * @param occasionType the occasion type it suits
   */
  public MealOccasionTypeKey(long mealId, OccasionType occasionType) {
    this.mealId = mealId;
    this.occasionType = occasionType;
  }
}
