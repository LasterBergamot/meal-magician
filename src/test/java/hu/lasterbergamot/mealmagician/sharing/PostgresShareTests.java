package hu.lasterbergamot.mealmagician.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Share links on the real database: Hibernate validates the {@code share_link} entity against the
 * Postgres schema at startup, the guarded revoke holds when two members revoke at once, and the
 * unique token hash refuses a duplicate. Not {@code @Transactional}, because the point is separate
 * transactions; the test removes its own rows.
 */
@Tag("integration")
class PostgresShareTests extends AbstractPostgresIntegrationTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private ShareLinkService shares;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private long household;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from household where name = 'Pg share'");
    jdbc.update("delete from app_user where google_sub like 'sub-pg-share-%'");
  }

  private long newHousehold(String person) {
    long user = HouseholdTestSupport.insertUser(jdbc, "pg-share-" + person);
    household = households.create(user, "Pg share").id();
    return user;
  }

  @Test
  void createOpenAndRevokeWorkOnPostgres() {
    long anna = newHousehold("anna");

    CreatedShareLink created = shares.create(anna, household, MONDAY.plusDays(3), null);

    assertThat(created.link().weekStart()).isEqualTo(MONDAY);
    assertThat(shares.open(created.token())).isPresent();
    shares.revoke(anna, household, created.link().id());
    assertThat(shares.open(created.token())).isEmpty();
    assertThat(shares.list(anna, household, MONDAY))
        .extracting(ShareLinkView::status)
        .containsExactly(ShareLinkStatus.REVOKED);
  }

  @Test
  void twoMembersRevokingTogetherBothSucceed() throws Exception {
    long anna = newHousehold("revoker");
    long linkId = shares.create(anna, household, MONDAY, null).link().id();

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        results.add(
            pool.submit(() -> {
              go.await();
              shares.revoke(anna, household, linkId);
              return null;
            }));
      }
      go.countDown();
      for (Future<?> result : results) {
        result.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            jdbc.queryForObject(
                "select count(*) from share_link where id = ? and revoked_at is not null",
                Long.class,
                linkId))
        .isEqualTo(1L);
  }

  @Test
  void theDatabaseRefusesTwoLinksWithTheSameTokenHash() {
    long anna = newHousehold("unique");
    shares.create(anna, household, MONDAY, null);
    String hash = jdbc.queryForObject("select token_hash from share_link", String.class);

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into share_link (household_id, token_hash, week_start, created_at,"
                    + " expires_at) values (?, ?, ?, now(), now() + interval '1 day')",
                household,
                hash,
                MONDAY))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theDatabaseRefusesALinkThatExpiresBeforeItWasCreated() {
    newHousehold("check");

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into share_link (household_id, token_hash, week_start, created_at,"
                    + " expires_at) values (?, 'x', ?, now(), now() - interval '1 day')",
                household,
                MONDAY))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
