package hu.lasterbergamot.mealmagician.household.internal;

import hu.lasterbergamot.mealmagician.household.HouseholdSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
