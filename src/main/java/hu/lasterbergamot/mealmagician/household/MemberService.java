package hu.lasterbergamot.mealmagician.household;

import hu.lasterbergamot.mealmagician.household.internal.HouseholdRepository;
import hu.lasterbergamot.mealmagician.household.internal.Membership;
import hu.lasterbergamot.mealmagician.household.internal.MembershipRepository;
import hu.lasterbergamot.mealmagician.identity.UserDirectory;
import hu.lasterbergamot.mealmagician.identity.UserProfile;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The household's life cycle after it exists (PLAN.md D8): who is in it, who may remove someone,
 * handing the household over, and leaving it.
 *
 * <p>The rule that matters: a household always has an owner. Leaving as the last owner is refused
 * ({@link MemberProblem#LAST_OWNER}), and handing over makes the new owner first and the old one a
 * plain member second, inside one transaction, so there is never a moment without one.
 *
 * <p>Every method that changes who is in charge first locks the household row, so two owners acting
 * at the same moment run one after the other. The membership check runs <em>after</em> the lock, so
 * it sees what the first one did: an owner who has just handed the household over is no longer an
 * owner when their second request gets its turn.
 *
 * <p>A removed person loses access at once without any extra step: the current household is checked
 * against the memberships on every request ({@code CurrentHouseholdResolver}).
 */
@Service
@Transactional
public class MemberService {

  private final HouseholdService households;
  private final HouseholdRepository householdRows;
  private final MembershipRepository memberships;
  private final UserDirectory directory;

  /**
   * Creates the service.
   *
   * @param households the household service, which does the membership and role check
   * @param householdRows persistence for households, to lock the row
   * @param memberships persistence for memberships
   * @param directory turns member ids into names and addresses
   */
  public MemberService(
      HouseholdService households,
      HouseholdRepository householdRows,
      MembershipRepository memberships,
      UserDirectory directory) {
    this.households = households;
    this.householdRows = householdRows;
    this.memberships = memberships;
    this.directory = directory;
  }

  /**
   * Lists the people in a household, owners first and then by name. Any member may look.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id of the person asking
   * @return the members
   * @throws HouseholdNotFoundException if the person is not a member
   */
  @Transactional(readOnly = true)
  public List<MemberView> list(long householdId, long userId) {
    households.require(householdId, userId);
    List<Membership> rows = memberships.findByHouseholdId(householdId);
    Map<Long, UserProfile> profiles =
        directory.find(rows.stream().map(Membership::getUserId).toList());
    return rows.stream()
        // The membership row references app_user with a foreign key, so the profile exists.
        .map(row -> view(row, profiles.get(row.getUserId())))
        .sorted(
            Comparator.comparing((MemberView member) -> member.role() != HouseholdRole.OWNER)
                .thenComparing(member -> member.displayName().toLowerCase(Locale.ROOT))
                .thenComparing(MemberView::userId))
        .toList();
  }

  /**
   * Removes a member from the household. Owners only. The person loses access at once.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id of the owner asking
   * @param memberId the {@code app_user} id of the person to remove
   * @throws HouseholdNotFoundException if the asking person is not a member
   * @throws OwnerOnlyException if they are a member but not an owner
   * @throws MemberRefusedException with {@link MemberProblem#SELF} if the owner named themselves
   *     (use {@link #leave})
   * @throws MemberNotFoundException if the other person is not in this household
   */
  public void remove(long householdId, long userId, long memberId) {
    requireOwnerLocked(householdId, userId);
    if (memberId == userId) {
      throw new MemberRefusedException(MemberProblem.SELF);
    }
    if (memberships.deleteMember(householdId, memberId) == 0) {
      throw new MemberNotFoundException(memberId);
    }
  }

  /**
   * Leaves the household. Any member may, except the last owner.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id of the person leaving
   * @throws HouseholdNotFoundException if the person is not a member
   * @throws MemberRefusedException with {@link MemberProblem#LAST_OWNER} if they are the only
   *     owner; they must hand ownership to someone first
   */
  public void leave(long householdId, long userId) {
    lock(householdId);
    CurrentHousehold current = households.require(householdId, userId);
    if (current.isOwner()
        && memberships.countByHouseholdIdAndRole(householdId, HouseholdRole.OWNER) <= 1) {
      throw new MemberRefusedException(MemberProblem.LAST_OWNER);
    }
    memberships.deleteMember(householdId, userId);
  }

  /**
   * Hands the household over: the other member becomes an owner and the asking owner becomes a
   * plain member. Owners only.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id of the owner handing over
   * @param memberId the {@code app_user} id of the member who takes over
   * @throws HouseholdNotFoundException if the asking person is not a member
   * @throws OwnerOnlyException if they are a member but not an owner
   * @throws MemberRefusedException with {@link MemberProblem#SELF} if the owner named themselves
   * @throws MemberNotFoundException if the other person is not in this household
   */
  public void transferOwnership(long householdId, long userId, long memberId) {
    requireOwnerLocked(householdId, userId);
    if (memberId == userId) {
      throw new MemberRefusedException(MemberProblem.SELF);
    }
    if (memberships.updateRole(householdId, memberId, HouseholdRole.OWNER) == 0) {
      throw new MemberNotFoundException(memberId);
    }
    memberships.updateRole(householdId, userId, HouseholdRole.MEMBER);
  }

  private void requireOwnerLocked(long householdId, long userId) {
    lock(householdId);
    if (!households.require(householdId, userId).isOwner()) {
      throw new OwnerOnlyException(householdId);
    }
  }

  private void lock(long householdId) {
    householdRows
        .findForUpdate(householdId)
        .orElseThrow(() -> new HouseholdNotFoundException(householdId));
  }

  private static MemberView view(Membership row, UserProfile profile) {
    return new MemberView(row.getUserId(), profile.displayName(), profile.email(), row.getRole());
  }
}
