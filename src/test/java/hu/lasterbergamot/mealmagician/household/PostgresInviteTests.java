package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The invite mappings and the guarded updates on the real database: Hibernate validates the {@code
 * invite} entity against the Postgres schema at startup, and the single-use {@code UPDATE} runs on
 * Postgres, not just on H2.
 */
@Transactional
class PostgresInviteTests extends AbstractPostgresIntegrationTest {

  @Autowired private HouseholdService households;
  @Autowired private InviteService invites;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void inviteJoinAndSingleUseWorkOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-inv-anna");
    long gabi = HouseholdTestSupport.insertUser(jdbc, "pg-inv-gabi");
    long annas = households.create(anna, "Annas").id();

    CreatedInvite created = invites.create(annas, anna, "Gabi@Example.com", InviteExpiry.HOURS_2);
    assertThat(invites.preview(created.code(), "gabi@example.com").householdName())
        .isEqualTo("Annas");
    invites.join(created.code(), gabi, "gabi@example.com");

    assertThat(households.require(annas, gabi).role()).isEqualTo(HouseholdRole.MEMBER);
    assertThat(invites.list(annas, anna))
        .extracting(InviteView::status)
        .containsExactly(InviteStatus.USED);
    assertThat(
            jdbc.queryForObject(
                "select source from allowed_email where email = 'gabi@example.com'", String.class))
        .isEqualTo("INVITE");
  }
}
