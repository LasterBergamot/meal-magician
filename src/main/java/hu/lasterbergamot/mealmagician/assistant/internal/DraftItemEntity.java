package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One proposed meal of a draft (table {@code assistant_draft_item}): either a library meal ({@code
 * mealId} set) or an idea that is not in the library yet ({@code ideaName} set), never both; the
 * database checks it. Adding an idea to the library turns the item into a library item.
 *
 * <p>Tags and ingredients of an idea are stored as names separated by a line feed. The service
 * collapses whitespace in every name first, so a name never contains one.
 */
@Entity
@Table(name = "assistant_draft_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftItemEntity {

  /** Separates the names in the stored tag and ingredient lists. */
  static final String SEPARATOR = "\n";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "draft_id", nullable = false, updatable = false)
  private long draftId;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(name = "day_date", nullable = false, updatable = false)
  private LocalDate dayDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "slot_type", nullable = false, updatable = false)
  private SlotType slotType;

  @Column(nullable = false, updatable = false)
  private int position;

  @Column(name = "meal_id")
  private Long mealId;

  @Column(name = "idea_name")
  private String ideaName;

  @Column(name = "idea_reason")
  private String ideaReason;

  @Column(name = "idea_tags")
  private String ideaTags;

  @Column(name = "idea_ingredients")
  private String ideaIngredients;

  private DraftItemEntity(
      long draftId, long householdId, LocalDate dayDate, SlotType slotType, int position) {
    this.draftId = draftId;
    this.householdId = householdId;
    this.dayDate = dayDate;
    this.slotType = slotType;
    this.position = position;
  }

  /**
   * Creates an item that names a library meal.
   *
   * @param draftId the draft
   * @param householdId the household
   * @param dayDate the day
   * @param slotType the slot type
   * @param position the place in that day and slot type, counted from 0
   * @param mealId the library meal, already checked against the household's catalog
   * @return the new item
   */
  public static DraftItemEntity library(
      long draftId,
      long householdId,
      LocalDate dayDate,
      SlotType slotType,
      int position,
      long mealId) {
    DraftItemEntity item = new DraftItemEntity(draftId, householdId, dayDate, slotType, position);
    item.mealId = mealId;
    return item;
  }

  /**
   * Creates an item that is an idea, not yet a library meal. The text is expected to be cleaned and
   * within the column sizes already (see {@link DraftValidator}).
   *
   * @param draftId the draft
   * @param householdId the household
   * @param dayDate the day
   * @param slotType the slot type
   * @param position the place in that day and slot type, counted from 0
   * @param name the idea's name, at most 100 characters
   * @param reason why the model suggests it, at most 300 characters, or {@code null}
   * @param tags the tag names joined with a line feed, at most 300 characters, or {@code null}
   * @param ingredients the ingredient names joined with a line feed, at most 500 characters, or
   *     {@code null}
   * @return the new item
   */
  public static DraftItemEntity idea(
      long draftId,
      long householdId,
      LocalDate dayDate,
      SlotType slotType,
      int position,
      String name,
      String reason,
      String tags,
      String ingredients) {
    DraftItemEntity item = new DraftItemEntity(draftId, householdId, dayDate, slotType, position);
    item.ideaName = name;
    item.ideaReason = reason;
    item.ideaTags = tags;
    item.ideaIngredients = ingredients;
    return item;
  }

  /**
   * Turns an idea into a library item after its meal was created: sets the meal and clears the
   * idea, as the database's either-or check requires.
   *
   * @param newMealId the id of the meal that was created from the idea
   */
  public void resolveTo(long newMealId) {
    this.mealId = newMealId;
    this.ideaName = null;
    this.ideaReason = null;
    this.ideaTags = null;
    this.ideaIngredients = null;
  }
}
