package hu.lasterbergamot.mealmagician.household;

import hu.lasterbergamot.mealmagician.household.internal.CodeHasher;
import hu.lasterbergamot.mealmagician.household.internal.HouseholdRepository;
import hu.lasterbergamot.mealmagician.household.internal.Invite;
import hu.lasterbergamot.mealmagician.household.internal.InviteRepository;
import hu.lasterbergamot.mealmagician.household.internal.Membership;
import hu.lasterbergamot.mealmagician.household.internal.MembershipRepository;
import hu.lasterbergamot.mealmagician.identity.AllowlistEditor;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email-bound household invites (PLAN.md D9): an owner invites an address and shares the {@code
 * /join/<code>} link by any messenger; the invited person opens it, signs in with Google and joins.
 *
 * <p>What keeps it safe:
 *
 * <ul>
 *   <li>The code is 256 random bits from {@link SecureRandom}. Only its SHA-256 hash is stored, and
 *       the code is returned once, by {@link #create}. It is never logged and appears in no
 *       exception message.
 *   <li>Joining needs the signed-in Google e-mail to equal the invited one, so a forwarded link is
 *       useless to anyone else.
 *   <li>An invite expires, can be revoked and works once. The single use is enforced by a guarded
 *       {@code UPDATE} (see {@link InviteRepository}), not by a read followed by a write.
 *   <li>Owner checks and tenant scoping happen in here, so a controller cannot forget them: a
 *       non-member gets {@link HouseholdNotFoundException} (404), a plain member gets {@link
 *       OwnerOnlyException} (403), and another household's invite id gets {@link
 *       InviteNotFoundException} (404).
 * </ul>
 */
@Service
@Transactional
public class InviteService {

  private static final int CODE_BYTES = 32;
  private static final int MAX_EMAIL_LENGTH = 254;
  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

  private final HouseholdService households;
  private final HouseholdRepository householdRows;
  private final InviteRepository invites;
  private final MembershipRepository memberships;
  private final AllowlistEditor allowlist;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /**
   * Creates the service.
   *
   * @param households the household service, which does the membership and role check
   * @param householdRows persistence for households, to read the name for the join page
   * @param invites persistence for invites
   * @param memberships persistence for memberships
   * @param allowlist lets an invited address sign in
   * @param clock the application clock
   */
  public InviteService(
      HouseholdService households,
      HouseholdRepository householdRows,
      InviteRepository invites,
      MembershipRepository memberships,
      AllowlistEditor allowlist,
      Clock clock) {
    this.households = households;
    this.householdRows = householdRows;
    this.invites = invites;
    this.memberships = memberships;
    this.allowlist = allowlist;
    this.clock = clock;
  }

  /**
   * Invites an address to a household and allows it to sign in. Owners only.
   *
   * @param householdId the household to invite to
   * @param userId the {@code app_user} id of the person asking
   * @param email the address to invite, in any case
   * @param expiry how long the link should work; {@code null} means the default of 24 hours
   * @return the invite and its raw code, which cannot be shown again
   * @throws HouseholdNotFoundException if the person is not a member
   * @throws OwnerOnlyException if the person is a member but not an owner
   * @throws InvalidInviteEmailException if the address is blank or not shaped like an e-mail
   *     address
   */
  public CreatedInvite create(long householdId, long userId, String email, InviteExpiry expiry) {
    requireOwner(householdId, userId);
    String normalized = allowlist.normalize(email);
    if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
      throw new InvalidInviteEmailException();
    }
    InviteExpiry chosen = expiry == null ? InviteExpiry.HOURS_24 : expiry;
    String code = newCode();
    Instant now = Instant.now(clock);
    Invite invite =
        invites.save(
            new Invite(
                householdId,
                normalized,
                CodeHasher.hash(code),
                now.plus(chosen.duration()),
                userId,
                now));
    allowlist.allowInvitedAddress(normalized, userId);
    return new CreatedInvite(view(invite, now), code);
  }

  /**
   * Lists a household's invites, newest first, including used, revoked and expired ones so the
   * owner can see what happened. Owners only.
   *
   * @param householdId the household
   * @param userId the {@code app_user} id of the person asking
   * @return the invites; never the codes
   * @throws HouseholdNotFoundException if the person is not a member
   * @throws OwnerOnlyException if the person is a member but not an owner
   */
  @Transactional(readOnly = true)
  public List<InviteView> list(long householdId, long userId) {
    requireOwner(householdId, userId);
    Instant now = Instant.now(clock);
    return invites.findByHouseholdIdOrderByCreatedAtDescIdDesc(householdId).stream()
        .map(invite -> view(invite, now))
        .toList();
  }

  /**
   * Withdraws a pending invite. Revoking one that was already used, revoked or expired changes
   * nothing. The address stays on the allowlist: removing it is the admin page's job (#45).
   *
   * @param householdId the household the invite belongs to
   * @param userId the {@code app_user} id of the person asking
   * @param inviteId the invite to revoke
   * @throws HouseholdNotFoundException if the person is not a member
   * @throws OwnerOnlyException if the person is a member but not an owner
   * @throws InviteNotFoundException if the invite does not exist in this household
   */
  public void revoke(long householdId, long userId, long inviteId) {
    requireOwner(householdId, userId);
    invites
        .findByIdAndHouseholdId(inviteId, householdId)
        .orElseThrow(() -> new InviteNotFoundException(inviteId));
    invites.markRevoked(inviteId, householdId, Instant.now(clock));
  }

  /**
   * Checks a join link without using it, to show the person which household they are about to
   * enter.
   *
   * @param code the code from the link
   * @param email the signed-in person's Google e-mail
   * @return the household the invite is for
   * @throws InviteRefusedException if the link cannot be used, with the reason
   */
  @Transactional(readOnly = true)
  public InvitePreview preview(String code, String email) {
    Invite invite = usable(code, email);
    return new InvitePreview(invite.getHouseholdId(), householdName(invite));
  }

  /**
   * Joins the household the link is for, as a plain member, and uses the invite up. A person who
   * already belongs to the household just uses the invite up.
   *
   * @param code the code from the link
   * @param userId the {@code app_user} id of the signed-in person
   * @param email the signed-in person's Google e-mail
   * @return the id of the household that was joined
   * @throws InviteRefusedException if the link cannot be used, with the reason
   */
  public long join(String code, long userId, String email) {
    Invite invite = usable(code, email);
    if (invites.markUsed(invite.getId(), userId, Instant.now(clock)) == 0) {
      // Somebody else used or revoked it between the check above and this update.
      throw new InviteRefusedException(InviteProblem.USED);
    }
    long householdId = invite.getHouseholdId();
    if (memberships.findByHouseholdIdAndUserId(householdId, userId).isEmpty()) {
      memberships.save(
          new Membership(householdId, userId, HouseholdRole.MEMBER, Instant.now(clock)));
    }
    return householdId;
  }

  /** The invite for this code, if the signed-in e-mail may use it right now. */
  private Invite usable(String code, String email) {
    Invite invite =
        invites
            .findByCodeHash(CodeHasher.hash(code))
            .orElseThrow(() -> new InviteRefusedException(InviteProblem.UNKNOWN));
    // The e-mail is checked first, so a link that is not yours tells you nothing about its state.
    if (!invite.getEmail().equals(allowlist.normalize(email))) {
      throw new InviteRefusedException(InviteProblem.WRONG_EMAIL);
    }
    return switch (status(invite, Instant.now(clock))) {
      case REVOKED -> throw new InviteRefusedException(InviteProblem.REVOKED);
      case USED -> throw new InviteRefusedException(InviteProblem.USED);
      case EXPIRED -> throw new InviteRefusedException(InviteProblem.EXPIRED);
      case PENDING -> invite;
    };
  }

  private String householdName(Invite invite) {
    // The invite row references the household with a foreign key, so it exists.
    return householdRows.findById(invite.getHouseholdId()).orElseThrow().getName();
  }

  private void requireOwner(long householdId, long userId) {
    if (!households.require(householdId, userId).isOwner()) {
      throw new OwnerOnlyException(householdId);
    }
  }

  private static InviteView view(Invite invite, Instant now) {
    return new InviteView(
        invite.getId(), invite.getEmail(), status(invite, now), invite.getExpiresAt());
  }

  private static InviteStatus status(Invite invite, Instant now) {
    if (invite.getUsedAt() != null) {
      return InviteStatus.USED;
    }
    if (invite.getRevokedAt() != null) {
      return InviteStatus.REVOKED;
    }
    return now.isBefore(invite.getExpiresAt()) ? InviteStatus.PENDING : InviteStatus.EXPIRED;
  }

  private String newCode() {
    byte[] bytes = new byte[CODE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
