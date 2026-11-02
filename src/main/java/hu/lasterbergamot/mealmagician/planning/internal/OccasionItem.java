package hu.lasterbergamot.mealmagician.planning.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One meal on an {@link Occasion}'s menu, at a place counted from 0. It has no note and no version:
 * adding only appends, and removing or moving an item that is already gone is a plain "not found".
 */
@Entity
@Table(name = "occasion_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OccasionItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occasion_id", nullable = false, updatable = false)
  private long occasionId;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false)
  private int position;

  @Column(name = "meal_id", nullable = false)
  private long mealId;

  /**
   * Creates a menu item.
   *
   * @param occasionId the occasion it belongs to
   * @param householdId the household of the occasion and of the meal
   * @param position the place on the menu, counted from 0
   * @param mealId the library meal, already checked to belong to the household
   */
  public OccasionItem(long occasionId, long householdId, int position, long mealId) {
    this.occasionId = occasionId;
    this.householdId = householdId;
    this.position = position;
    this.mealId = mealId;
  }

  /**
   * Sets the place of the item on its menu. The service keeps the menu contiguous; this method only
   * records the value.
   *
   * @param newPosition the new place, counted from 0
   */
  public void reposition(int newPosition) {
    this.position = newPosition;
  }
}
