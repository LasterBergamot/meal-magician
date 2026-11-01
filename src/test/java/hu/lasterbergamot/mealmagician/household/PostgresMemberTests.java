package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The member queries on the real database: the {@code FOR UPDATE} lock, the bulk delete and update
 * statements and the owner count run on Postgres, not just on H2.
 */
@Transactional
class PostgresMemberTests extends AbstractPostgresIntegrationTest {

  @Autowired private MemberService members;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void listTransferLeaveAndRemoveWorkOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-m-anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-m-bela");
    long cili = HouseholdTestSupport.insertUser(jdbc, "pg-m-cili");
    long id = households.create(anna, "Annas").id();
    for (long member : new long[] {bela, cili}) {
      jdbc.update(
          "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
          id,
          member);
    }

    assertThat(members.list(id, bela))
        .extracting(MemberView::userId)
        .containsExactly(anna, bela, cili);
    assertThatThrownBy(() -> members.leave(id, anna)).isInstanceOf(MemberRefusedException.class);

    members.transferOwnership(id, anna, bela);
    members.leave(id, anna);
    members.remove(id, bela, cili);

    assertThat(members.list(id, bela)).extracting(MemberView::userId).containsExactly(bela);
  }
}
