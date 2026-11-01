package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.household.internal.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The household rules, against the dev profile's H2 so the mappings meet the real schema. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class HouseholdServiceTest {

  @Autowired private HouseholdService service;
  @Autowired private MembershipRepository memberships;
  @Autowired private JdbcTemplate jdbc;

  private long anna;
  private long bela;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    bela = HouseholdTestSupport.insertUser(jdbc, "bela");
  }

  @Test
  void theCreatorBecomesTheOwner() {
    HouseholdSummary created = service.create(anna, "Nagy család");

    assertThat(created.name()).isEqualTo("Nagy család");
    assertThat(created.role()).isEqualTo(HouseholdRole.OWNER);
    assertThat(service.require(created.id(), anna).isOwner()).isTrue();
  }

  @Test
  void surroundingBlanksOfTheNameAreIgnored() {
    assertThat(service.create(anna, "  Home  ").name()).isEqualTo("Home");
  }

  @Test
  void aNameOfExactlyTheMaximumLengthIsAccepted() {
    String longest = "x".repeat(HouseholdService.MAX_NAME_LENGTH);

    assertThat(service.create(anna, longest).name()).isEqualTo(longest);
  }

  @Test
  void blankMissingAndTooLongNamesAreRejected() {
    assertThatThrownBy(() -> service.create(anna, "   "))
        .isInstanceOf(InvalidHouseholdNameException.class);
    assertThatThrownBy(() -> service.create(anna, null))
        .isInstanceOf(InvalidHouseholdNameException.class);
    assertThatThrownBy(() -> service.create(anna, "x".repeat(101)))
        .isInstanceOf(InvalidHouseholdNameException.class)
        .hasMessageContaining("100");
  }

  @Test
  void listsOnlyMyHouseholdsByNameIgnoringCase() {
    service.create(anna, "beta");
    service.create(anna, "Alfa");
    service.create(bela, "Not mine");

    assertThat(service.listFor(anna))
        .extracting(HouseholdSummary::name)
        .containsExactly("Alfa", "beta");
    assertThat(service.listFor(HouseholdTestSupport.insertUser(jdbc, "nobody"))).isEmpty();
  }

  @Test
  void anotherHouseholdIsNotFoundNotForbidden() {
    long annasId = service.create(anna, "Annas").id();

    assertThat(service.find(annasId, bela)).isEmpty();
    assertThat(service.find(-1, anna)).isEmpty();
    assertThatThrownBy(() -> service.require(annasId, bela))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void anOwnerCanRename() {
    long id = service.create(anna, "Old").id();

    CurrentHousehold renamed = service.rename(id, anna, " New ");

    assertThat(renamed.name()).isEqualTo("New");
    assertThat(service.require(id, anna).name()).isEqualTo("New");
  }

  @Test
  void aPlainMemberCannotRename() {
    long id = service.create(anna, "Annas").id();
    memberships.save(
        new hu.lasterbergamot.mealmagician.household.internal.Membership(
            id, bela, HouseholdRole.MEMBER, java.time.Instant.now()));

    assertThat(service.require(id, bela).isOwner()).isFalse();
    assertThatThrownBy(() -> service.rename(id, bela, "Mine now"))
        .isInstanceOf(OwnerOnlyException.class);
    assertThat(service.require(id, anna).name()).isEqualTo("Annas");
  }

  @Test
  void anOutsiderCannotRenameAndLearnsNothing() {
    long id = service.create(anna, "Annas").id();

    assertThatThrownBy(() -> service.rename(id, bela, "Mine now"))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void renamingToABadNameFails() {
    long id = service.create(anna, "Annas").id();

    assertThatThrownBy(() -> service.rename(id, anna, ""))
        .isInstanceOf(InvalidHouseholdNameException.class);
  }
}
