package hu.lasterbergamot.mealmagician.catalog.internal;

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
 * A label a household puts on its meals, such as "vegetarian" or "quick". Tags belong to one
 * household: the same word in two households is two rows. {@code nameKey} is the lower-case name
 * and is what makes "Vegan" and "vegan" the same tag.
 */
@Entity
@Table(name = "tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(name = "name_key", nullable = false, updatable = false)
  private String nameKey;

  /**
   * Creates a tag.
   *
   * @param householdId the household that owns it
   * @param name the name as the household typed it, already cleaned
   * @param nameKey the lower-case form of {@code name}
   */
  public Tag(long householdId, String name, String nameKey) {
    this.householdId = householdId;
    this.name = name;
    this.nameKey = nameKey;
  }
}
