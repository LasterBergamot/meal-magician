package hu.lasterbergamot.mealmagician.catalog.internal;

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
 * A meal in a household's library: something the household cooks and plans, with optional notes and
 * a recipe link. The tags are not a JPA relation here; they are rows of {@link MealTag}, so the tag
 * links can be replaced without loading and rewriting the meal.
 *
 * <p>{@code version} makes two people editing the same meal at once a visible conflict instead of a
 * silently lost edit.
 */
@Entity
@Table(name = "meal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false)
  private String name;

  private String notes;

  @Column(name = "recipe_url")
  private String recipeUrl;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  /**
   * Creates a meal that is not archived.
   *
   * @param householdId the household that owns it
   * @param name the already validated name
   * @param notes the validated notes, or {@code null} for none
   * @param recipeUrl the validated link, or {@code null} for none
   * @param createdAt when it was added
   */
  public Meal(long householdId, String name, String notes, String recipeUrl, Instant createdAt) {
    this.householdId = householdId;
    this.name = name;
    this.notes = notes;
    this.recipeUrl = recipeUrl;
    this.createdAt = createdAt;
  }

  /**
   * Replaces the name, notes and recipe link. The tags are changed separately, by the service.
   *
   * @param newName the already validated name
   * @param newNotes the validated notes, or {@code null} for none
   * @param newRecipeUrl the validated link, or {@code null} for none
   */
  public void change(String newName, String newNotes, String newRecipeUrl) {
    this.name = newName;
    this.notes = newNotes;
    this.recipeUrl = newRecipeUrl;
  }

  /** Takes the meal out of the default library. Its history stays intact. */
  public void archive() {
    this.archived = true;
  }

  /** Puts an archived meal back into the default library. */
  public void restore() {
    this.archived = false;
  }
}
