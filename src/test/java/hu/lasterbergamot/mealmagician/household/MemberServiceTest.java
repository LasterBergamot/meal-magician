package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The member rules (PLAN.md D8) against the real schema. Anna owns "Annas" with Bela and Cili as
 * plain members; Dora owns "Doras" and has nothing to do with Anna's household; Emil belongs
 * nowhere.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class MemberServiceTest {

  @Autowired private MemberService members;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;
  private long cili;
  private long dora;
  private long emil;
  private long annas;
  private long doras;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "Bela");
    cili = HouseholdTestSupport.insertUser(jdbc, "cili");
    dora = HouseholdTestSupport.insertUser(jdbc, "dora");
    emil = HouseholdTestSupport.insertUser(jdbc, "emil");
    annas = households.create(anna, "Annas").id();
    doras = households.create(dora, "Doras").id();
    addMember(annas, cili);
    addMember(annas, bela);
  }

  private void addMember(long householdId, long userId) {
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
        householdId,
        userId);
  }

  private void makeOwner(long householdId, long userId) {
    jdbc.update(
        "update membership set role = 'OWNER' where household_id = ? and user_id = ?",
        householdId,
        userId);
  }

  @Test
  void listsOwnersFirstThenByNameIgnoringCase() {
    assertThat(members.list(annas, bela))
        .extracting(MemberView::displayName, MemberView::role)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("anna", HouseholdRole.OWNER),
            org.assertj.core.groups.Tuple.tuple("Bela", HouseholdRole.MEMBER),
            org.assertj.core.groups.Tuple.tuple("cili", HouseholdRole.MEMBER));
  }

  @Test
  void aListEntryCarriesTheEmailAndId() {
    assertThat(members.list(annas, anna))
        .first()
        .isEqualTo(new MemberView(anna, "anna", "anna@example.com", HouseholdRole.OWNER));
  }

  @Test
  void anOutsiderCannotListMembers() {
    assertThatThrownBy(() -> members.list(annas, emil))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> members.list(annas, dora))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void anOwnerRemovesAMemberWhoThenLosesAccess() {
    members.remove(annas, anna, bela);

    assertThat(households.find(annas, bela)).isEmpty();
    assertThat(households.listFor(bela)).isEmpty();
    assertThat(members.list(annas, anna))
        .extracting(MemberView::userId)
        .containsExactly(anna, cili);
  }

  @Test
  void aPlainMemberMayNotRemoveAnyone() {
    assertThatThrownBy(() -> members.remove(annas, bela, cili))
        .isInstanceOf(OwnerOnlyException.class);
    assertThat(households.find(annas, cili)).isPresent();
  }

  @Test
  void anOutsiderMayNotRemoveAnyone() {
    assertThatThrownBy(() -> members.remove(annas, emil, cili))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> members.remove(annas, dora, cili))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThat(households.find(annas, cili)).isPresent();
  }

  @Test
  void anOwnerCannotRemoveThemselves() {
    assertThatThrownBy(() -> members.remove(annas, anna, anna))
        .isInstanceOfSatisfying(
            MemberRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(MemberProblem.SELF));
    assertThat(households.find(annas, anna)).isPresent();
  }

  @Test
  void removingSomeoneFromAnotherHouseholdIsNotFound() {
    assertThatThrownBy(() -> members.remove(annas, anna, dora))
        .isInstanceOf(MemberNotFoundException.class);
    assertThatThrownBy(() -> members.remove(annas, anna, emil))
        .isInstanceOf(MemberNotFoundException.class);
    assertThat(households.find(doras, dora)).isPresent();
  }

  @Test
  void aMemberCanLeave() {
    members.leave(annas, bela);

    assertThat(households.find(annas, bela)).isEmpty();
    assertThat(households.find(annas, anna)).isPresent();
  }

  @Test
  void theLastOwnerCannotLeaveEvenWithOtherMembers() {
    assertThatThrownBy(() -> members.leave(annas, anna))
        .isInstanceOfSatisfying(
            MemberRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(MemberProblem.LAST_OWNER));
    assertThat(households.require(annas, anna).isOwner()).isTrue();
  }

  @Test
  void theLastOwnerAloneInTheHouseholdCannotLeaveEither() {
    assertThatThrownBy(() -> members.leave(doras, dora)).isInstanceOf(MemberRefusedException.class);
    assertThat(households.find(doras, dora)).isPresent();
  }

  @Test
  void anOwnerCanLeaveWhenAnotherOwnerRemains() {
    makeOwner(annas, bela);

    members.leave(annas, anna);

    assertThat(households.find(annas, anna)).isEmpty();
    assertThat(households.require(annas, bela).isOwner()).isTrue();
  }

  @Test
  void anOutsiderCannotLeave() {
    assertThatThrownBy(() -> members.leave(annas, emil))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> members.leave(annas + 1000, anna))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void transferMakesTheOtherPersonOwnerAndTheOldOwnerAMember() {
    members.transferOwnership(annas, anna, bela);

    assertThat(households.require(annas, bela).role()).isEqualTo(HouseholdRole.OWNER);
    assertThat(households.require(annas, anna).role()).isEqualTo(HouseholdRole.MEMBER);
  }

  @Test
  void afterTransferTheOldOwnerMayLeaveAndHasLostTheOwnerRights() {
    members.transferOwnership(annas, anna, bela);

    assertThatThrownBy(() -> members.remove(annas, anna, cili))
        .isInstanceOf(OwnerOnlyException.class);
    members.leave(annas, anna);
    assertThat(households.find(annas, anna)).isEmpty();
  }

  @Test
  void aPlainMemberMayNotTransfer() {
    assertThatThrownBy(() -> members.transferOwnership(annas, bela, cili))
        .isInstanceOf(OwnerOnlyException.class);
    assertThat(households.require(annas, anna).isOwner()).isTrue();
  }

  @Test
  void anOutsiderMayNotTransfer() {
    assertThatThrownBy(() -> members.transferOwnership(annas, emil, bela))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void anOwnerCannotTransferToThemselves() {
    assertThatThrownBy(() -> members.transferOwnership(annas, anna, anna))
        .isInstanceOfSatisfying(
            MemberRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(MemberProblem.SELF));
    assertThat(households.require(annas, anna).isOwner()).isTrue();
  }

  @Test
  void transferToSomeoneOutsideTheHouseholdIsNotFoundAndChangesNothing() {
    assertThatThrownBy(() -> members.transferOwnership(annas, anna, dora))
        .isInstanceOf(MemberNotFoundException.class);
    assertThat(households.require(annas, anna).isOwner()).isTrue();
    assertThat(households.require(doras, dora).isOwner()).isTrue();
    assertThat(households.find(annas, dora)).isEmpty();
  }

  @Test
  void everyProblemHasAMessageKey() {
    for (MemberProblem problem : MemberProblem.values()) {
      assertThat(problem.messageKey()).startsWith("household.members.error.");
    }
  }
}
