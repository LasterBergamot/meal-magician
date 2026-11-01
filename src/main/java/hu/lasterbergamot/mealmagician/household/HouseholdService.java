package hu.lasterbergamot.mealmagician.household;

import hu.lasterbergamot.mealmagician.household.internal.Household;
import hu.lasterbergamot.mealmagician.household.internal.HouseholdRepository;
import hu.lasterbergamot.mealmagician.household.internal.Membership;
import hu.lasterbergamot.mealmagician.household.internal.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The household module's public entry point: creating, renaming and looking up households.
 *
 * <p>Every method takes the asking person's id and checks their membership itself, so a caller
 * cannot forget the tenant check (PLAN.md D8): another household's id gives {@link
 * HouseholdNotFoundException} (404), a member doing an owner-only action gives {@link
 * OwnerOnlyException} (403).
 */
@Service
@Transactional
public class HouseholdService {

  /** The longest household name, matching the {@code household.name} column. */
  public static final int MAX_NAME_LENGTH = 100;

  private final HouseholdRepository households;
  private final MembershipRepository memberships;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param households persistence for households
   * @param memberships persistence for memberships
   * @param clock the application clock
   */
  public HouseholdService(
      HouseholdRepository households, MembershipRepository memberships, Clock clock) {
    this.households = households;
    this.memberships = memberships;
    this.clock = clock;
  }

  /**
   * Creates a household and makes the person its owner.
   *
   * @param userId the {@code app_user} id of the creator
   * @param name the wanted name; surrounding blanks are ignored
   * @return the new household as the creator sees it
   * @throws InvalidHouseholdNameException if the name is blank or longer than {@link
   *     #MAX_NAME_LENGTH}
   */
  public HouseholdSummary create(long userId, String name) {
    String cleanName = cleanName(name);
    Instant now = Instant.now(clock);
    Household household = households.save(new Household(cleanName, now));
    memberships.save(new Membership(household.getId(), userId, HouseholdRole.OWNER, now));
    return new HouseholdSummary(household.getId(), household.getName(), HouseholdRole.OWNER);
  }

  /**
   * Lists the households a person belongs to, by name.
   *
   * @param userId the {@code app_user} id
   * @return the person's households; empty for someone who has none yet
   */
  @Transactional(readOnly = true)
  public List<HouseholdSummary> listFor(long userId) {
    return memberships.findSummariesByUserId(userId);
  }

  /**
   * Looks a household up for a person, if they belong to it. This is the membership check every
   * request goes through.
   *
   * @param householdId the household id, which may come from a URL or the session and so is not
   *     trusted
   * @param userId the {@code app_user} id
   * @return the household, or empty if it does not exist or the person is not a member
   */
  @Transactional(readOnly = true)
  public Optional<CurrentHousehold> find(long householdId, long userId) {
    return memberships
        .findByHouseholdIdAndUserId(householdId, userId)
        .flatMap(membership ->
            households
                .findById(householdId)
                .map(household ->
                    new CurrentHousehold(
                        household.getId(), household.getName(), membership.getRole())));
  }

  /**
   * Like {@link #find}, but for callers that cannot continue without the household.
   *
   * @param householdId the household id
   * @param userId the {@code app_user} id
   * @return the household as the person sees it
   * @throws HouseholdNotFoundException if it does not exist or the person is not a member
   */
  @Transactional(readOnly = true)
  public CurrentHousehold require(long householdId, long userId) {
    return find(householdId, userId).orElseThrow(() -> new HouseholdNotFoundException(householdId));
  }

  /**
   * Renames a household. Only owners may.
   *
   * @param householdId the household to rename
   * @param userId the {@code app_user} id of the person asking
   * @param name the wanted name; surrounding blanks are ignored
   * @return the household after the change
   * @throws HouseholdNotFoundException if the person is not a member
   * @throws OwnerOnlyException if the person is a member but not an owner
   * @throws InvalidHouseholdNameException if the name is blank or too long
   * @throws org.springframework.dao.OptimisticLockingFailureException if someone renamed it at the
   *     same moment; the controller shows a "reload" message
   */
  public CurrentHousehold rename(long householdId, long userId, String name) {
    CurrentHousehold current = require(householdId, userId);
    if (!current.isOwner()) {
      throw new OwnerOnlyException(householdId);
    }
    String cleanName = cleanName(name);
    Household household = households.findById(householdId).orElseThrow();
    household.rename(cleanName);
    households.flush();
    return new CurrentHousehold(householdId, cleanName, current.role());
  }

  private static String cleanName(String name) {
    String trimmed = name == null ? "" : name.strip();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      throw new InvalidHouseholdNameException();
    }
    return trimmed;
  }
}
