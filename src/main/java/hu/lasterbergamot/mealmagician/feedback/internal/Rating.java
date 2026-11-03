package hu.lasterbergamot.mealmagician.feedback.internal;

import hu.lasterbergamot.mealmagician.feedback.Thumb;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One member's rating of one planned slot. It copies the slot's date, type and meal, and has no
 * foreign key to the slot, so the rating outlives a slot that is removed or replaced (D27).
 *
 * <p>There is no {@code @Version}: only the author changes a rating, and the unique key on slot and
 * user already stops two rows, even when two tabs give at once (see {@link RatingWriter}).
 */
@Entity
@Table(name = "rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rating {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(name = "slot_id", nullable = false, updatable = false)
  private long slotId;

  @Column(name = "meal_id", nullable = false)
  private long mealId;

  @Column(name = "slot_date", nullable = false)
  private LocalDate slotDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "slot_type", nullable = false)
  private SlotType slotType;

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Thumb thumb;

  // The column is SMALLINT (1 to 5); Hibernate validates the type, so say so.
  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer stars;

  @Column(name = "comment_text")
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Creates a rating.
   *
   * @param householdId the household
   * @param slotId the slot that is rated (it may be removed later)
   * @param mealId the meal planned in the slot
   * @param slotDate the slot's day
   * @param slotType the slot's type
   * @param userId the author
   * @param thumb up or down
   * @param stars 1 to 5, or {@code null}
   * @param comment the cleaned comment, or {@code null}
   * @param now the creation time, also used as the first update time
   */
  public Rating(
      long householdId,
      long slotId,
      long mealId,
      LocalDate slotDate,
      SlotType slotType,
      long userId,
      Thumb thumb,
      Integer stars,
      String comment,
      Instant now) {
    this.householdId = householdId;
    this.slotId = slotId;
    this.mealId = mealId;
    this.slotDate = slotDate;
    this.slotType = slotType;
    this.userId = userId;
    this.thumb = thumb;
    this.stars = stars;
    this.comment = comment;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * Replaces what the author said and refreshes the copy of the slot, in case the slot was moved or
   * its meal changed since the first rating.
   *
   * @param mealId the meal planned in the slot now
   * @param slotDate the slot's day now
   * @param slotType the slot's type now
   * @param thumb up or down
   * @param stars 1 to 5, or {@code null}
   * @param comment the cleaned comment, or {@code null}
   * @param now the time of the change
   */
  public void update(
      long mealId,
      LocalDate slotDate,
      SlotType slotType,
      Thumb thumb,
      Integer stars,
      String comment,
      Instant now) {
    this.mealId = mealId;
    this.slotDate = slotDate;
    this.slotType = slotType;
    this.thumb = thumb;
    this.stars = stars;
    this.comment = comment;
    this.updatedAt = now;
  }
}
