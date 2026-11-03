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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Share links on the real database: Hibernate validates the {@code share_link} entity against the
 * Postgres schema at startup, the guarded revoke holds when two members revoke at once, and the
 * unique token hash refuses a duplicate. Not {@code @Transactional}, because the point is separate
 * transactions; the test removes its own rows.
 */
@Tag("integration")
class PostgresShareTests extends AbstractPostgresIntegrationTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  /** How many times the race is repeated; one round rarely overlaps. */
  private static final int RACE_ROUNDS = 40;

  @Autowired private ShareLinkService shares;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;

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

  /** Replaces the household's links with {@code count} active ones, without the service. */
  private void seedActiveLinks(int count) {
    jdbc.update("delete from share_link where household_id = ?", household);
    jdbc.update(
        "insert into share_link (household_id, token_hash, week_start, created_at, expires_at)"
            + " select ?, md5(random()::text) || md5(random()::text), ?, now(),"
            + " now() + interval '1 day' from generate_series(1, ?)",
        household,
        MONDAY,
        count);
  }

  private long activeLinks() {
    return jdbc.queryForObject(
        "select count(*) from share_link where household_id = ? and revoked_at is null",
        Long.class,
        household);
  }

  private long addMember(String person) {
    long member = HouseholdTestSupport.insertUser(jdbc, "pg-share-" + person);
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at)"
            + " values (?, ?, 'MEMBER', now())",
        household,
        member);
    return member;
  }

  /**
   * The cap holds under a real race: with nine links, two members create at the same moment, many
   * times over. Each round must end with exactly ten links, one success and one refusal. A single
   * round rarely overlaps, so the rounds are repeated; the test below shows the lock itself.
   */
  @Test
  void twoMembersCreatingTheTenthLinkTogetherGetExactlyOneLinkAndOneRefusal() throws Exception {
    long anna = newHousehold("racer");
    long bela = addMember("racer-bela");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < RACE_ROUNDS; round++) {
        seedActiveLinks(ShareLinkService.MAX_ACTIVE - 1);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (long member : new long[] {anna, bela}) {
          results.add(
              pool.submit(() -> {
                go.await();
                try {
                  shares.create(member, household, MONDAY, null);
                  return true;
                } catch (ShareLimitException e) {
                  return false;
                }
              }));
        }
        go.countDown();
        int created = 0;
        for (Future<Boolean> result : results) {
          created += result.get() ? 1 : 0;
        }

        assertThat(created).as("round %d", round).isEqualTo(1);
        assertThat(activeLinks()).as("round %d", round).isEqualTo(ShareLinkService.MAX_ACTIVE);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * The lock itself, without depending on timing: one transaction takes the household row lock, a
   * create from another thread must then wait for it (Postgres shows a session waiting on a lock),
   * and it goes through once the first transaction ends. Without the lock the create would finish
   * at once and no session would ever wait.
   */
  @Test
  void aCreateWaitsForAnyoneHoldingTheHouseholdRowLock() throws Exception {
    long anna = newHousehold("locker");
    seedActiveLinks(ShareLinkService.MAX_ACTIVE - 1);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // Hold the lock in this thread's transaction until the watcher has seen the create wait.
      Boolean sawWaiter =
          new TransactionTemplate(transactions)
              .execute(status -> {
                households.requireForUpdate(household, anna);
                pool.submit(() -> shares.create(anna, household, MONDAY, null));
                try {
                  return pool.submit(this::someSessionIsWaitingOnALock).get();
                } catch (InterruptedException | ExecutionException e) {
                  throw new IllegalStateException(e);
                }
              });

      assertThat(sawWaiter).as("a create waited for the lock").isTrue();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
    // The lock was released, so the waiting create ran and reached the cap.
    assertThat(activeLinks()).isEqualTo(ShareLinkService.MAX_ACTIVE);
  }

  /** Polls Postgres for up to five seconds for a session that is waiting on a lock. */
  private boolean someSessionIsWaitingOnALock() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      Long waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where wait_event_type = 'Lock'", Long.class);
      if (waiting != null && waiting > 0) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
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
