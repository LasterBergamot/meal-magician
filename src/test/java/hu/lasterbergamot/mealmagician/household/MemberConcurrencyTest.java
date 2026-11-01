package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Two owners leaving at the same moment must not leave the household without an owner. Each leave
 * sees "there is another owner" if it reads before the other one deletes; the household row lock
 * makes them run one after the other. Not {@code @Transactional}, because the point is two separate
 * transactions.
 */
@SpringBootTest
@ActiveProfiles("dev")
class MemberConcurrencyTest {

  @Autowired private MemberService members;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from membership");
    jdbc.update("delete from household");
    jdbc.update("delete from app_user where google_sub in ('sub-carol', 'sub-dave')");
  }

  @Test
  void twoOwnersLeavingTogetherLeaveExactlyOneOwner() throws Exception {
    long carol = HouseholdTestSupport.insertUser(jdbc, "carol");
    long dave = HouseholdTestSupport.insertUser(jdbc, "dave");
    long id = households.create(carol, "Shared").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'OWNER', now())",
        id,
        dave);

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (long owner : new long[] {carol, dave}) {
        results.add(
            pool.submit(() -> {
              go.await();
              try {
                members.leave(id, owner);
                return true;
              } catch (MemberRefusedException e) {
                return false;
              }
            }));
      }
      go.countDown();
      int left = 0;
      for (Future<Boolean> result : results) {
        left += result.get() ? 1 : 0;
      }

      assertThat(left).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from membership where household_id = ? and role = 'OWNER'",
                Long.class,
                id))
        .isEqualTo(1L);
  }
}
