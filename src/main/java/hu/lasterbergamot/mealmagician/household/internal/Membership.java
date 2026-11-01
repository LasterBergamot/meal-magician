package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One person's place in one household. The ids are plain numbers, not JPA relations: the user lives
 * in the {@code identity} module, and modules refer to each other's data by id only.
 */
@Entity
@Table(name = "membership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "household_id", nullable = false, updatable = false)
  private long householdId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HouseholdRole role;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  /**
   * Creates a membership.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id
   * @param role the role in that household
   * @param joinedAt when the person joined
   */
  public Membership(long householdId, long userId, HouseholdRole role, Instant joinedAt) {
    this.householdId = householdId;
    this.userId = userId;
    this.role = role;
    this.joinedAt = joinedAt;
  }
}
