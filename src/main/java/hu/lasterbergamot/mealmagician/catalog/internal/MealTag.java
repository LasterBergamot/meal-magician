package hu.lasterbergamot.mealmagician.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One tag on one meal. The row also carries the household, which the composite foreign keys in the
 * migration check against the meal and the tag, so a link can never cross households.
 */
@Entity
@Table(name = "meal_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealTag {

  @EmbeddedId private MealTagKey id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  /**
   * Creates a link between a meal and a tag.
   *
   * @param householdId the household both belong to
   * @param mealId the meal
   * @param tagId the tag
   */
  public MealTag(long householdId, long mealId, long tagId) {
    this.id = new MealTagKey(mealId, tagId);
    this.householdId = householdId;
  }
}
