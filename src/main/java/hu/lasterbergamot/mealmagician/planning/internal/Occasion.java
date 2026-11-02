package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A dated event with its own menu, such as a birthday or Christmas. The menu is a list of {@link
 * OccasionItem} rows. {@code version} makes two people editing or deleting the same occasion at
 * once a visible conflict instead of a lost edit; menu changes do not touch it.
 */
@Entity
@Table(name = "occasion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Occasion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false)
  private String name;

  // Named occasionDate in Java; the column is "date", as in MealSlot.
  @Column(name = "date", nullable = false)
  private LocalDate occasionDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "occasion_type", nullable = false)
  private OccasionType type;

  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  /**
   * Creates an occasion.
   *
   * @param householdId the household that owns it
   * @param name the already cleaned name
   * @param occasionDate the day of the occasion
   * @param type the kind of occasion
   * @param notes the cleaned notes, or {@code null} for none
   * @param createdAt when it was added
   */
  public Occasion(
      long householdId,
      String name,
      LocalDate occasionDate,
      OccasionType type,
      String notes,
      Instant createdAt) {
    this.householdId = householdId;
    this.name = name;
    this.occasionDate = occasionDate;
    this.type = type;
    this.notes = notes;
    this.createdAt = createdAt;
  }

  /**
   * Replaces the editable fields. The service has already cleaned them.
   *
   * @param newName the new name
   * @param newDate the new day
   * @param newType the new kind of occasion
   * @param newNotes the new notes, or {@code null} for none
   */
  public void change(String newName, LocalDate newDate, OccasionType newType, String newNotes) {
    this.name = newName;
    this.occasionDate = newDate;
    this.type = newType;
    this.notes = newNotes;
  }
}
