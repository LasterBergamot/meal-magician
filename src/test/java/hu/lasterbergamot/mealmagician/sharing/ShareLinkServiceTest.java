package hu.lasterbergamot.mealmagician.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.shared.security.TokenCodes;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The share-link rules (PLAN.md D28) against the real schema. Anna owns "Annas", Bela is a plain
 * member of it, Cili owns "Cilis" and has nothing to do with Anna's household. Time is moved by
 * rewriting timestamps in the table, because the clock is the real one.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ShareLinkServiceTest {

  /** A Monday. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private ShareLinkService shares;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private long anna;
  private long bela;
  private long cili;
  private long dora;
  private long annas;
  private long cilis;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    dora = HouseholdTestSupport.insertUser(jdbc, "dora");
    annas = households.create(anna, "Annas").id();
    cilis = households.create(cili, "Cilis").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
        annas,
        bela);
  }

  private CreatedShareLink create() {
    return shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_7);
  }

  private void expire(long linkId) {
    jdbc.update(
        "update share_link set created_at = ?, expires_at = ? where id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(3))),
        Timestamp.from(Instant.now().minusSeconds(60)),
        linkId);
    entityManager.clear();
  }

  @Test
  void createStoresOnlyTheHashOfTheToken() {
    CreatedShareLink created = create();

    String stored = jdbc.queryForObject("select token_hash from share_link", String.class);
    assertThat(created.token()).hasSize(43);
    assertThat(stored).hasSize(64).isEqualTo(TokenCodes.hash(created.token()));
    assertThat(stored).doesNotContain(created.token());
    assertThat(created.link().status()).isEqualTo(ShareLinkStatus.ACTIVE);
    assertThat(created.link().weekStart()).isEqualTo(MONDAY);
  }

  @Test
  void theListedLinkNamesTheMemberWhoMadeIt() {
    CreatedShareLink created = shares.create(bela, annas, MONDAY, ShareExpiry.DAYS_7);

    assertThat(created.link().createdBy()).isEqualTo(bela);
    assertThat(shares.list(anna, annas, MONDAY))
        .singleElement()
        .extracting(ShareLinkView::createdBy)
        .isEqualTo(bela);
  }

  @Test
  void theRawTokenOpensTheWeek() {
    CreatedShareLink created = create();

    assertThat(shares.open(created.token()))
        .hasValueSatisfying(week -> assertThat(week.monday()).isEqualTo(MONDAY));
  }

  @Test
  void aWednesdayIsMovedBackToThatWeeksMonday() {
    CreatedShareLink created = shares.create(bela, annas, MONDAY.plusDays(2), null);

    assertThat(created.link().weekStart()).isEqualTo(MONDAY);
    assertThat(jdbc.queryForObject("select week_start from share_link", LocalDate.class))
        .isEqualTo(MONDAY);
  }

  @Test
  void theExpiryChoiceSetsTheLifetime() {
    Instant before = Instant.now();

    assertThat(shares.create(anna, annas, MONDAY, ShareExpiry.DAY_1).link().expiresAt())
        .isBetween(
            before.plus(Duration.ofDays(1)), before.plus(Duration.ofDays(1)).plusSeconds(30));
    assertThat(shares.create(anna, annas, MONDAY, ShareExpiry.DAYS_30).link().expiresAt())
        .isAfter(before.plus(Duration.ofDays(29)));
    assertThat(shares.create(anna, annas, MONDAY, null).link().expiresAt())
        .isBetween(
            before.plus(Duration.ofDays(7)), before.plus(Duration.ofDays(7)).plusSeconds(30));
  }

  @Test
  void expiryCodesRoundTripAndUnknownOnesFallBackToTheDefault() {
    for (ShareExpiry expiry : ShareExpiry.values()) {
      assertThat(ShareExpiry.fromCode(expiry.code())).isEqualTo(expiry);
    }
    assertThat(ShareExpiry.fromCode("forever")).isEqualTo(ShareExpiry.DAYS_7);
    assertThat(ShareExpiry.fromCode(null)).isEqualTo(ShareExpiry.DAYS_7);
  }

  @Test
  void everyLinkGetsADifferentToken() {
    assertThat(create().token()).isNotEqualTo(create().token());
  }

  @Test
  void anEleventhActiveLinkIsRefused() {
    for (int i = 0; i < ShareLinkService.MAX_ACTIVE; i++) {
      create();
    }

    assertThatThrownBy(this::create).isInstanceOf(ShareLimitException.class);
    assertThat(jdbc.queryForObject("select count(*) from share_link", Integer.class)).isEqualTo(10);
  }

  @Test
  void aRevokedAndAnExpiredLinkDoNotCountTowardsTheLimit() {
    CreatedShareLink revoked = create();
    CreatedShareLink expired = create();
    for (int i = 0; i < ShareLinkService.MAX_ACTIVE - 2; i++) {
      create();
    }
    shares.revoke(anna, annas, revoked.link().id());
    expire(expired.link().id());

    assertThat(create().link().status()).isEqualTo(ShareLinkStatus.ACTIVE);
    assertThat(create().link().status()).isEqualTo(ShareLinkStatus.ACTIVE);
    assertThatThrownBy(this::create).isInstanceOf(ShareLimitException.class);
  }

  @Test
  void theLimitIsPerHousehold() {
    for (int i = 0; i < ShareLinkService.MAX_ACTIVE; i++) {
      create();
    }

    assertThat(shares.create(cili, cilis, MONDAY, null).link().status())
        .isEqualTo(ShareLinkStatus.ACTIVE);
  }

  @Test
  void createPurgesThisHouseholdsLinksThatExpiredMoreThanThirtyDaysAgo() {
    CreatedShareLink old = create();
    CreatedShareLink recent = create();
    CreatedShareLink others = shares.create(cili, cilis, MONDAY, null);
    jdbc.update(
        "update share_link set created_at = ?, expires_at = ? where id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(40))),
        Timestamp.from(Instant.now().minus(Duration.ofDays(31))),
        old.link().id());
    jdbc.update(
        "update share_link set created_at = ?, expires_at = ? where id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(40))),
        Timestamp.from(Instant.now().minus(Duration.ofDays(29))),
        recent.link().id());
    jdbc.update(
        "update share_link set created_at = ?, expires_at = ? where id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(40))),
        Timestamp.from(Instant.now().minus(Duration.ofDays(35))),
        others.link().id());
    entityManager.clear();

    create();

    assertThat(jdbc.queryForList("select id from share_link", Long.class))
        .contains(recent.link().id(), others.link().id())
        .doesNotContain(old.link().id());
  }

  @Test
  void aNonMemberCannotCreateListOrRevoke() {
    CreatedShareLink created = create();

    assertThatThrownBy(() -> shares.create(dora, annas, MONDAY, null))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> shares.list(dora, annas, MONDAY))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> shares.revoke(dora, annas, created.link().id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThat(shares.open(created.token())).isPresent();
  }

  @Test
  void listShowsActiveExpiredAndRevokedLinksOfThatWeekOnly() {
    CreatedShareLink active = create();
    CreatedShareLink expired = create();
    CreatedShareLink revoked = create();
    shares.create(anna, annas, MONDAY.plusWeeks(1), null);
    shares.revoke(bela, annas, revoked.link().id());
    expire(expired.link().id());

    // expire() moves created_at back three days, so the expired link is the oldest.
    List<ShareLinkView> list = shares.list(bela, annas, MONDAY.plusDays(4));

    assertThat(list)
        .extracting(ShareLinkView::id, ShareLinkView::status)
        .containsExactly(
            tuple(revoked.link().id(), ShareLinkStatus.REVOKED),
            tuple(active.link().id(), ShareLinkStatus.ACTIVE),
            tuple(expired.link().id(), ShareLinkStatus.EXPIRED));
  }

  @Test
  void listNeverExposesTheTokenOrItsHash() {
    CreatedShareLink created = create();
    String hash = TokenCodes.hash(created.token());

    assertThat(shares.list(anna, annas, MONDAY).toString())
        .doesNotContain(created.token())
        .doesNotContain(hash);
  }

  @Test
  void aRevokedLinkNoLongerOpens() {
    CreatedShareLink created = create();

    shares.revoke(bela, annas, created.link().id());

    assertThat(shares.open(created.token())).isEmpty();
  }

  @Test
  void revokingTwiceIsFine() {
    CreatedShareLink created = create();

    shares.revoke(anna, annas, created.link().id());
    shares.revoke(anna, annas, created.link().id());

    assertThat(shares.open(created.token())).isEmpty();
  }

  @Test
  void anotherHouseholdsLinkIsNotFoundAndStaysOpen() {
    CreatedShareLink created = create();

    assertThatThrownBy(() -> shares.revoke(cili, cilis, created.link().id()))
        .isInstanceOf(ShareLinkNotFoundException.class);
    assertThat(shares.open(created.token())).isPresent();
  }

  @Test
  void aMissingLinkIsNotFound() {
    assertThatThrownBy(() -> shares.revoke(anna, annas, 987654L))
        .isInstanceOf(ShareLinkNotFoundException.class);
  }

  @Test
  void openGivesNothingForAMalformedToken() {
    assertThat(shares.open(null)).isEmpty();
    assertThat(shares.open("short")).isEmpty();
    assertThat(shares.open("not a token!")).isEmpty();
  }

  @Test
  void openGivesNothingForAnUnknownToken() {
    assertThat(shares.open(TokenCodes.generate())).isEmpty();
  }

  @Test
  void openGivesNothingForAnExpiredToken() {
    CreatedShareLink created = create();

    expire(created.link().id());

    assertThat(shares.open(created.token())).isEmpty();
  }

  @Test
  void openDoesNotChangeTheRow() {
    CreatedShareLink created = create();
    entityManager.flush();
    String before = jdbc.queryForMap("select * from share_link").toString();

    shares.open(created.token());
    entityManager.flush();

    assertThat(jdbc.queryForMap("select * from share_link").toString()).isEqualTo(before);
  }
}
