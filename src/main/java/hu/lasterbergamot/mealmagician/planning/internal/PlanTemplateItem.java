package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One meal in a {@link PlanTemplate}: which day (as an offset from the first day), which slot type,
 * which place in that group and which note. Items are frozen with the template, so there are no
 * setters and no version. The household id is stored too, so the composite foreign keys refuse a
 * template or meal of another household.
 */
@Entity
@Table(name = "plan_template_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanTemplateItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "template_id", nullable = false, updatable = false)
  private long templateId;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(name = "day_offset", nullable = false)
  private int dayOffset;

  @Enumerated(EnumType.STRING)
  @Column(name = "slot_type", nullable = false)
  private SlotType slotType;

  @Column(nullable = false)
  private int position;

  @Column(name = "meal_id", nullable = false)
  private long mealId;

  private String note;

  /**
   * Creates a template item.
   *
   * @param templateId the template it belongs to
   * @param householdId the household of the template and of the meal
   * @param dayOffset days after the first day, 0 to 6
   * @param slotType the slot type
   * @param position the place in the group of this day offset and slot type, counted from 0
   * @param mealId the library meal, already checked to belong to the household
   * @param note the note copied from the slot, or {@code null} for none
   */
  public PlanTemplateItem(
      long templateId,
      long householdId,
      int dayOffset,
      SlotType slotType,
      int position,
      long mealId,
      String note) {
    this.templateId = templateId;
    this.householdId = householdId;
    this.dayOffset = dayOffset;
    this.slotType = slotType;
    this.position = position;
    this.mealId = mealId;
    this.note = note;
  }
}
