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

/** One meal on an {@link OccasionTemplate}'s menu. Frozen with the template, so no setters. */
@Entity
@Table(name = "occasion_template_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OccasionTemplateItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "template_id", nullable = false, updatable = false)
  private long templateId;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false)
  private int position;

  @Column(name = "meal_id", nullable = false)
  private long mealId;

  /**
   * Creates a template menu item.
   *
   * @param templateId the occasion template it belongs to
   * @param householdId the household of the template and of the meal
   * @param position the place on the menu, counted from 0
   * @param mealId the library meal, already checked to belong to the household
   */
  public OccasionTemplateItem(long templateId, long householdId, int position, long mealId) {
    this.templateId = templateId;
    this.householdId = householdId;
    this.position = position;
    this.mealId = mealId;
  }
}
