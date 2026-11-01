package hu.lasterbergamot.mealmagician.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The composite primary key of a {@link MealTag}: one meal and one tag. JPA needs it as its own
 * type, and it must be {@link Serializable} with equal values for equal links.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealTagKey implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "meal_id", nullable = false, updatable = false)
  private long mealId;

  @Column(name = "tag_id", nullable = false, updatable = false)
  private long tagId;

  /**
   * Creates the key of one link.
   *
   * @param mealId the meal
   * @param tagId the tag
   */
  public MealTagKey(long mealId, long tagId) {
    this.mealId = mealId;
    this.tagId = tagId;
  }
}
