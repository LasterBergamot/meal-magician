package hu.lasterbergamot.mealmagician.planning.internal;

import hu.lasterbergamot.mealmagician.planning.TemplateScope;
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
 * A saved day or week of the plan that can be applied again. Its meals are {@link PlanTemplateItem}
 * rows. A template is frozen: it can be renamed or deleted but never edited, so {@code version}
 * only guards the rename and the delete against two people acting at once.
 */
@Entity
@Table(name = "plan_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TemplateScope scope;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  /**
   * Creates a template.
   *
   * @param householdId the household that owns it
   * @param name the already cleaned name
   * @param scope whether it covers a day or a week
   * @param createdAt when it was saved
   */
  public PlanTemplate(long householdId, String name, TemplateScope scope, Instant createdAt) {
    this.householdId = householdId;
    this.name = name;
    this.scope = scope;
    this.createdAt = createdAt;
  }

  /**
   * Gives the template a new name.
   *
   * @param newName the already cleaned name
   */
  public void rename(String newName) {
    this.name = newName;
  }
}
