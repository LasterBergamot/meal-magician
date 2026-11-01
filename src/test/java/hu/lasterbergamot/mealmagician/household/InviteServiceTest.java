package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;

import hu.lasterbergamot.mealmagician.household.internal.CodeHasher;
import hu.lasterbergamot.mealmagician.household.internal.InviteRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The invite rules (PLAN.md D9) against the real schema: who may invite, what is stored, and every
 * way a join link can be refused. Anna owns "Annas", Bela is a plain member of it, Cili owns
 * "Cilis" and has nothing to do with Anna's household.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class InviteServiceTest {

  private static final String GUEST = "guest@example.com";

  @Autowired private InviteService invites;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @MockitoSpyBean private InviteRepository spiedInvites;

  private long anna;
  private long bela;
  private long cili;
  private long guest;
  private long annas;
  private long cilis;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    guest = HouseholdTestSupport.insertUser(jdbc, "guest");
    annas = households.create(anna, "Annas").id();
    cilis = households.create(cili, "Cilis").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
        annas,
        bela);
  }

  private CreatedInvite invite() {
    return invites.create(annas, anna, GUEST, InviteExpiry.HOURS_24);
  }

  private void makeExpired(CreatedInvite created) {
    jdbc.update(
        "update invite set expires_at = ? where id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
        created.invite().id());
    entityManager.clear();
  }

  @Test
  void creatingAnInviteStoresOnlyTheHashOfTheCode() {
    CreatedInvite created = invite();

    String stored = jdbc.queryForObject("select code_hash from invite", String.class);
    assertThat(created.code()).hasSizeGreaterThanOrEqualTo(43);
    assertThat(stored).hasSize(64).isEqualTo(CodeHasher.hash(created.code()));
    assertThat(stored).doesNotContain(created.code());
    assertThat(created.invite().status()).isEqualTo(InviteStatus.PENDING);
    assertThat(created.invite().email()).isEqualTo(GUEST);
  }

  @Test
  void everyInviteGetsADifferentCode() {
    assertThat(invite().code()).isNotEqualTo(invite().code());
  }

  @Test
  void theInvitedAddressIsAddedToTheAllowlistWithTheInviteSource() {
    invites.create(annas, anna, "  Guest@Example.COM ", InviteExpiry.HOURS_24);
    entityManager.flush();

    assertThat(
            jdbc.queryForMap("select source, added_by from allowed_email where email = ?", GUEST))
        .containsEntry("SOURCE", "INVITE")
        .containsEntry("ADDED_BY", anna);
  }

  @Test
  void theExpiryChoiceSetsTheLifetime() {
    Instant before = Instant.now();

    assertThat(invites.create(annas, anna, GUEST, InviteExpiry.HOURS_2).invite().expiresAt())
        .isBetween(
            before.plus(Duration.ofHours(2)), before.plus(Duration.ofHours(2)).plusSeconds(30));
    assertThat(invites.create(annas, anna, GUEST, InviteExpiry.DAYS_3).invite().expiresAt())
        .isAfter(before.plus(Duration.ofDays(2)));
    assertThat(invites.create(annas, anna, GUEST, null).invite().expiresAt())
        .isBetween(
            before.plus(Duration.ofHours(24)), before.plus(Duration.ofHours(24)).plusSeconds(30));
  }

  @Test
  void expiryCodesRoundTripAndUnknownOnesFallBackToTheDefault() {
    for (InviteExpiry expiry : InviteExpiry.values()) {
      assertThat(InviteExpiry.fromCode(expiry.code())).isEqualTo(expiry);
    }
    assertThat(InviteExpiry.fromCode("forever")).isEqualTo(InviteExpiry.HOURS_24);
    assertThat(InviteExpiry.fromCode(null)).isEqualTo(InviteExpiry.HOURS_24);
  }

  @Test
  void badAddressesAreRejected() {
    for (String bad :
        new String[] {null, "", "  ", "no-at-sign", "a@b", "two@@example.com", "a b@example.com"}) {
      assertThatThrownBy(() -> invites.create(annas, anna, bad, InviteExpiry.HOURS_24))
          .as("address %s", bad)
          .isInstanceOf(InvalidInviteEmailException.class);
    }
    String tooLong = "a".repeat(250) + "@example.com";
    assertThatThrownBy(() -> invites.create(annas, anna, tooLong, InviteExpiry.HOURS_24))
        .isInstanceOf(InvalidInviteEmailException.class);
    assertThat(jdbc.queryForObject("select count(*) from invite", Long.class)).isZero();
  }

  @Test
  void onlyAnOwnerMayCreateListOrRevoke() {
    CreatedInvite created = invite();

    assertThatThrownBy(() -> invites.create(annas, bela, GUEST, InviteExpiry.HOURS_24))
        .isInstanceOf(OwnerOnlyException.class);
    assertThatThrownBy(() -> invites.list(annas, bela)).isInstanceOf(OwnerOnlyException.class);
    assertThatThrownBy(() -> invites.revoke(annas, bela, created.invite().id()))
        .isInstanceOf(OwnerOnlyException.class);
  }

  @Test
  void aStrangerGetsNotFoundNotForbidden() {
    CreatedInvite created = invite();

    assertThatThrownBy(() -> invites.create(annas, cili, GUEST, InviteExpiry.HOURS_24))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> invites.list(annas, cili))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> invites.revoke(annas, cili, created.invite().id()))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void anOwnerCannotTouchAnotherHouseholdsInvite() {
    CreatedInvite created = invite();

    assertThatThrownBy(() -> invites.revoke(cilis, cili, created.invite().id()))
        .isInstanceOf(InviteNotFoundException.class);
    assertThat(invites.list(annas, anna))
        .extracting(InviteView::status)
        .containsExactly(InviteStatus.PENDING);
    assertThat(invites.list(cilis, cili)).isEmpty();
  }

  @Test
  void theListShowsEveryStatusNewestFirst() {
    CreatedInvite expired = invites.create(annas, anna, "old@example.com", InviteExpiry.HOURS_2);
    makeExpired(expired);
    CreatedInvite revoked =
        invites.create(annas, anna, "revoked@example.com", InviteExpiry.HOURS_24);
    invites.revoke(annas, anna, revoked.invite().id());
    CreatedInvite used = invites.create(annas, anna, "used@example.com", InviteExpiry.HOURS_24);
    long usedBy = HouseholdTestSupport.insertUser(jdbc, "used");
    invites.join(used.code(), usedBy, "used@example.com");
    invites.create(annas, anna, GUEST, InviteExpiry.HOURS_24);

    assertThat(invites.list(annas, anna))
        .extracting(InviteView::email, InviteView::status)
        .containsExactly(
            tuple(GUEST, InviteStatus.PENDING),
            tuple("used@example.com", InviteStatus.USED),
            tuple("revoked@example.com", InviteStatus.REVOKED),
            tuple("old@example.com", InviteStatus.EXPIRED));
  }

  @Test
  void revokingTwiceOrAfterUseChangesNothing() {
    CreatedInvite created = invite();
    invites.join(created.code(), guest, GUEST);

    invites.revoke(annas, anna, created.invite().id());

    assertThat(invites.list(annas, anna))
        .extracting(InviteView::status)
        .containsExactly(InviteStatus.USED);
  }

  @Test
  void joiningMakesThePersonAPlainMemberAndUsesTheInviteUp() {
    CreatedInvite created = invite();

    InvitePreview preview = invites.preview(created.code(), "GUEST@example.com");
    long joined = invites.join(created.code(), guest, "GUEST@example.com");

    assertThat(preview).isEqualTo(new InvitePreview(annas, "Annas"));
    assertThat(joined).isEqualTo(annas);
    assertThat(households.require(annas, guest).role()).isEqualTo(HouseholdRole.MEMBER);
    assertThat(jdbc.queryForObject("select used_by from invite", Long.class)).isEqualTo(guest);
  }

  @Test
  void aLinkWorksOnlyOnce() {
    CreatedInvite created = invite();
    invites.join(created.code(), guest, GUEST);

    assertRefused(() -> invites.join(created.code(), guest, GUEST), InviteProblem.USED);
    assertRefused(() -> invites.preview(created.code(), GUEST), InviteProblem.USED);
  }

  @Test
  void aMemberWhoJoinsAgainGetsNoSecondMembership() {
    CreatedInvite created = invites.create(annas, anna, "bela@example.com", InviteExpiry.HOURS_24);

    invites.join(created.code(), bela, "bela@example.com");

    assertThat(
            jdbc.queryForObject(
                "select count(*) from membership where household_id = ? and user_id = ?",
                Long.class,
                annas,
                bela))
        .isEqualTo(1);
    assertThat(households.require(annas, bela).role()).isEqualTo(HouseholdRole.MEMBER);
  }

  @Test
  void anUnknownCodeIsRefused() {
    assertRefused(() -> invites.preview("not-a-code", GUEST), InviteProblem.UNKNOWN);
    assertRefused(() -> invites.join(null, guest, GUEST), InviteProblem.UNKNOWN);
  }

  @Test
  void anotherGoogleAccountIsRefusedAndLearnsNothingElse() {
    CreatedInvite created = invite();
    invites.revoke(annas, anna, created.invite().id());

    assertRefused(
        () -> invites.preview(created.code(), "other@example.com"), InviteProblem.WRONG_EMAIL);
    assertRefused(
        () -> invites.join(created.code(), cili, "other@example.com"), InviteProblem.WRONG_EMAIL);
    assertThat(households.find(annas, cili)).isEmpty();
  }

  @Test
  void aRevokedLinkIsRefused() {
    CreatedInvite created = invite();
    invites.revoke(annas, anna, created.invite().id());

    assertRefused(() -> invites.join(created.code(), guest, GUEST), InviteProblem.REVOKED);
  }

  @Test
  void anExpiredLinkIsRefused() {
    CreatedInvite created = invite();
    makeExpired(created);

    assertRefused(() -> invites.join(created.code(), guest, GUEST), InviteProblem.EXPIRED);
    assertThat(households.find(annas, guest)).isEmpty();
  }

  @Test
  void losingTheRaceForTheLastUseIsRefusedAndJoinsNobody() {
    CreatedInvite created = invite();
    doReturn(0).when(spiedInvites).markUsed(anyLong(), anyLong(), any());

    assertRefused(() -> invites.join(created.code(), guest, GUEST), InviteProblem.USED);
    assertThat(households.find(annas, guest)).isEmpty();
  }

  private static void assertRefused(Runnable action, InviteProblem expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            InviteRefusedException.class, e -> assertThat(e.getProblem()).isEqualTo(expected));
  }
}
