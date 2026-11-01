package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import hu.lasterbergamot.mealmagician.household.HouseholdSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence for memberships, which is the table every tenant check reads. */
public interface MembershipRepository extends JpaRepository<Membership, Long> {

  /**
   * The tenant check: finds out whether this person is in this household, and in which role.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id
   * @return the membership, or empty if the person does not belong (or the household is unknown)
   */
  Optional<Membership> findByHouseholdIdAndUserId(long householdId, long userId);

  /**
   * Lists a person's households with their role in each.
   *
   * @param userId the {@code app_user} id
   * @return the households ordered by name, then id so the order is stable
   */
  @Query(
      """
      select new hu.lasterbergamot.mealmagician.household.HouseholdSummary(h.id, h.name, m.role)
      from Membership m join Household h on h.id = m.householdId
      where m.userId = :userId
      order by lower(h.name), h.id
      """)
  List<HouseholdSummary> findSummariesByUserId(long userId);

  /**
   * Lists everyone in a household.
   *
   * @param householdId the household, which the caller has checked the asking person belongs to
   * @return its memberships in no particular order
   */
  List<Membership> findByHouseholdId(long householdId);

  /**
   * Counts the people holding a role in a household. A query, not a cached entity, so the answer is
   * current inside the lock the member service holds.
   *
   * @param householdId the household
   * @param role the role to count
   * @return how many members have it
   */
  long countByHouseholdIdAndRole(long householdId, HouseholdRole role);

  /**
   * Removes one person from a household.
   *
   * @param householdId the household, so another household's membership is never touched
   * @param userId the {@code app_user} id to remove
   * @return {@code 1} if they were a member, {@code 0} if they were not
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from Membership m where m.householdId = :householdId and m.userId = :userId")
  int deleteMember(long householdId, long userId);

  /**
   * Changes a member's role.
   *
   * @param householdId the household, so another household's membership is never touched
   * @param userId the {@code app_user} id
   * @param role the new role
   * @return {@code 1} if they are a member, {@code 0} if they are not
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update Membership m set m.role = :role where m.householdId = :householdId and m.userId = :userId")
  int updateRole(long householdId, long userId, HouseholdRole role);
}
