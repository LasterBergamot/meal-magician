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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A saved occasion menu that can start a new occasion on any date: the name, type and notes of the
 * occasion it came from, plus {@link OccasionTemplateItem} rows for the menu. It has no date. The
 * {@code version} is there for a later rename; nothing changes it in v1.
 */
@Entity
@Table(name = "occasion_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OccasionTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "occasion_type", nullable = false)
  private OccasionType type;

  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  /**
   * Creates an occasion template.
   *
   * @param householdId the household that owns it
   * @param name the already cleaned name
   * @param type the kind of occasion
   * @param notes the cleaned notes, or {@code null} for none
   * @param createdAt when it was saved
   */
  public OccasionTemplate(
      long householdId, String name, OccasionType type, String notes, Instant createdAt) {
    this.householdId = householdId;
    this.name = name;
    this.type = type;
    this.notes = notes;
    this.createdAt = createdAt;
  }
}
