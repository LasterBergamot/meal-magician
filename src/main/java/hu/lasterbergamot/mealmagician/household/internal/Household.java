package hu.lasterbergamot.mealmagician.household.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A household: the group of people who plan their meals together and the owner of all planning data
 * (PLAN.md D8). {@code version} makes two members renaming it at once a visible conflict instead of
 * a silently lost edit.
 */
@Entity
@Table(name = "household")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Household {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  /**
   * Creates a household.
   *
   * @param name the already validated name
   * @param createdAt when it was created
   */
  public Household(String name, Instant createdAt) {
    this.name = name;
    this.createdAt = createdAt;
  }

  /**
   * Changes the name.
   *
   * @param newName the already validated name
   */
  public void rename(String newName) {
    this.name = newName;
  }
}
