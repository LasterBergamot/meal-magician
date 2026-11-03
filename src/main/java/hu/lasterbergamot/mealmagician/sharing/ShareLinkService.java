package hu.lasterbergamot.mealmagician.sharing;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.SharedWeek;
import hu.lasterbergamot.mealmagician.planning.SharedWeekReader;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import hu.lasterbergamot.mealmagician.sharing.internal.ShareLink;
import hu.lasterbergamot.mealmagician.sharing.internal.ShareLinkRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only share links to one week of a household's plan (PLAN.md D28). A member makes a link and
 * sends it by any messenger; whoever opens it sees the week without signing in.
 *
 * <p>What keeps it safe:
 *
 * <ul>
 *   <li>The token is 256 random bits ({@link TokenCodes}). Only its SHA-256 hash is stored, and the
 *       token is returned once, by {@link #create}. It is never logged and appears in no exception
 *       message.
 *   <li>A link covers one household and one ISO week and expires after 1, 7 or 30 days. It is
 *       active only while it is not revoked and not expired; the lookup query itself checks that.
 *   <li>The management methods check membership and scope by household, so another household's link
 *       id gives {@link ShareLinkNotFoundException} (404) and a non-member gets {@code
 *       HouseholdNotFoundException} (404).
 *   <li>{@link #open} is the only method without a person. It returns only a {@link SharedWeek},
 *       which holds no notes, ids or household fields, and it writes nothing.
 * </ul>
 */
@Service
@Transactional
public class ShareLinkService {

  /** How many links a household may have open at once. */
  public static final int MAX_ACTIVE = 10;

  /** Expired links are kept this long (so the list can still show them), then purged. */
  private static final Duration PURGE_AFTER = Duration.ofDays(30);

  private final ShareLinkRepository links;
  private final HouseholdService households;
  private final SharedWeekReader reader;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param links persistence for share links
   * @param households the household service, which does the membership check
   * @param reader the unchecked reader of a shared week
   * @param clock the application clock
   */
  public ShareLinkService(
      ShareLinkRepository links,
      HouseholdService households,
      SharedWeekReader reader,
      Clock clock) {
    this.links = links;
    this.households = households;
    this.reader = reader;
    this.clock = clock;
  }

  /**
   * Makes a share link for the week that contains a day. Any member may do it.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household whose week is shared
   * @param anyDayOfWeek any date in the week; it is moved back to that week's Monday
   * @param expiry how long the link should work; {@code null} means the default of 7 days
   * @return the link and its raw token, which cannot be shown again
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws ShareLimitException if the household already has {@value #MAX_ACTIVE} active links
   */
  public CreatedShareLink create(
      long userId, long householdId, LocalDate anyDayOfWeek, ShareExpiry expiry) {
    households.require(householdId, userId);
    Instant now = Instant.now(clock);
    if (links.countByHouseholdIdAndRevokedAtIsNullAndExpiresAtAfter(householdId, now)
        >= MAX_ACTIVE) {
      throw new ShareLimitException(MAX_ACTIVE);
    }
    // No scheduler (D11), so old rows are cleared here, one household at a time.
    links.deleteByHouseholdIdAndExpiresAtBefore(householdId, now.minus(PURGE_AFTER));
    ShareExpiry chosen = expiry == null ? ShareExpiry.DAYS_7 : expiry;
    String token = TokenCodes.generate();
    ShareLink link =
        links.save(
            new ShareLink(
                householdId,
                TokenCodes.hash(token),
                monday(anyDayOfWeek),
                userId,
                now,
                now.plus(chosen.duration())));
    return new CreatedShareLink(view(link, now), token);
  }

  /**
   * Lists the links made for a week, newest first, including expired and revoked ones. Any member
   * may do it.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household
   * @param anyDayOfWeek any date in the week
   * @return the links; never the tokens or their hashes
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   */
  @Transactional(readOnly = true)
  public List<ShareLinkView> list(long userId, long householdId, LocalDate anyDayOfWeek) {
    households.require(householdId, userId);
    Instant now = Instant.now(clock);
    return links
        .findByHouseholdIdAndWeekStartOrderByCreatedAtDescIdDesc(householdId, monday(anyDayOfWeek))
        .stream()
        .map(link -> view(link, now))
        .toList();
  }

  /**
   * Withdraws a link. Revoking one that is already revoked or expired changes nothing and is not an
   * error: two members clicking at once both succeed. Any member may do it.
   *
   * @param userId the {@code app_user} id of the person asking
   * @param householdId the household the link belongs to
   * @param linkId the link to revoke
   * @throws hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException if the person is
   *     not a member
   * @throws ShareLinkNotFoundException if the link does not exist in this household
   */
  public void revoke(long userId, long householdId, long linkId) {
    households.require(householdId, userId);
    links
        .findByIdAndHouseholdId(linkId, householdId)
        .orElseThrow(() -> new ShareLinkNotFoundException(linkId));
    // The row count is ignored on purpose: 0 only means "already revoked".
    links.revoke(linkId, householdId, Instant.now(clock));
  }

  /**
   * Opens a link: the public read path, with no signed-in person. The token is the only proof. It
   * writes nothing.
   *
   * @param token the text from the URL; any value is safe to pass
   * @return the shared week while the link is active, or empty for a malformed, unknown, expired or
   *     revoked token (the caller must answer all four the same way)
   */
  @Transactional(readOnly = true)
  public Optional<SharedWeek> open(String token) {
    if (!TokenCodes.isWellFormed(token)) {
      return Optional.empty();
    }
    return links
        .findActiveByTokenHash(TokenCodes.hash(token), Instant.now(clock))
        .map(link -> reader.read(link.getHouseholdId(), link.getWeekStart()));
  }

  private static LocalDate monday(LocalDate day) {
    return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static ShareLinkView view(ShareLink link, Instant now) {
    return new ShareLinkView(
        link.getId(),
        link.getWeekStart(),
        status(link, now),
        link.getCreatedAt(),
        link.getExpiresAt(),
        link.getCreatedBy());
  }

  private static ShareLinkStatus status(ShareLink link, Instant now) {
    if (link.getRevokedAt() != null) {
      return ShareLinkStatus.REVOKED;
    }
    return now.isBefore(link.getExpiresAt()) ? ShareLinkStatus.ACTIVE : ShareLinkStatus.EXPIRED;
  }
}
