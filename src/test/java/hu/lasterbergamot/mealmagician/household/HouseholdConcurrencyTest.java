package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.household.internal.Household;
import hu.lasterbergamot.mealmagician.household.internal.HouseholdRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Two people renaming at once: the second save is refused instead of silently overwriting the
 * first. Not {@code @Transactional}, because a conflict needs two separate transactions.
 */
@SpringBootTest
@ActiveProfiles("dev")
class HouseholdConcurrencyTest {

  @Autowired private HouseholdService service;
  @Autowired private HouseholdRepository households;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from membership");
    jdbc.update("delete from household");
    jdbc.update("delete from app_user where google_sub = 'sub-carol'");
  }

  @Test
  void aStaleRenameIsRefused() {
    long carol = HouseholdTestSupport.insertUser(jdbc, "carol");
    long id = service.create(carol, "Original").id();
    Household stale = households.findById(id).orElseThrow();

    service.rename(id, carol, "First wins");

    stale.rename("Second loses");
    assertThatThrownBy(() -> households.saveAndFlush(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(service.require(id, carol).name()).isEqualTo("First wins");
  }
}
