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
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One meal planned on one date in one slot type (breakfast, lunch, dinner or snack), with its place
 * in that group and an optional note. The plan is stored per date; a week is a query over seven
 * dates.
 *
 * <p>The meal and the household are set when the slot is created and never change: a different meal
 * means removing the slot and adding another. {@code version} makes two people moving or editing
 * the same slot at once a visible conflict instead of a lost edit.
 */
@Entity
@Table(name = "meal_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealSlot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  // Named slotDate in Java; the column is "date", as persistence.md and PLAN.md §4.2 call it.
  @Column(name = "date", nullable = false)
  private LocalDate slotDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "slot_type", nullable = false)
  private SlotType slotType;

  @Column(nullable = false)
  private int position;

  @Column(name = "meal_id", nullable = false, updatable = false)
  private long mealId;

  private String note;

  @Version private long version;

  /**
   * Creates a planned slot.
   *
   * @param householdId the household whose plan this is
   * @param slotDate the day the meal is planned for
   * @param slotType the slot type
   * @param position the place in the group of this date and slot type, counted from 0
   * @param mealId the library meal, already checked to belong to the household
   * @param note the cleaned note, or {@code null} for none
   */
  public MealSlot(
      long householdId,
      LocalDate slotDate,
      SlotType slotType,
      int position,
      long mealId,
      String note) {
    this.householdId = householdId;
    this.slotDate = slotDate;
    this.slotType = slotType;
    this.position = position;
    this.mealId = mealId;
    this.note = note;
  }

  /**
   * Puts the slot on another day and slot type, at a new place in that group.
   *
   * @param newDate the day to move to
   * @param newSlotType the slot type to move to
   * @param newPosition the place in the new group, counted from 0
   */
  public void moveTo(LocalDate newDate, SlotType newSlotType, int newPosition) {
    this.slotDate = newDate;
    this.slotType = newSlotType;
    this.position = newPosition;
  }

  /**
   * Sets the place of the slot inside its current group. The service keeps the group contiguous;
   * this method only records the value.
   *
   * @param newPosition the new place, counted from 0
   */
  public void reposition(int newPosition) {
    this.position = newPosition;
  }

  /**
   * Replaces the note of the slot.
   *
   * @param newNote the cleaned note, or {@code null} to clear it
   */
  public void changeNote(String newNote) {
    this.note = newNote;
  }
}
