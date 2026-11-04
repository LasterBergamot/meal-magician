package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import hu.lasterbergamot.mealmagician.shared.ClockConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The ledger on the real database: Hibernate validates the {@code assistant_usage} entity against
 * the Postgres schema at startup, and many simultaneous calls lose no usage. Not
 * {@code @Transactional}, because the point is separate transactions.
 */
@Tag("integration")
class PostgresUsageLedgerTests extends AbstractPostgresIntegrationTest {

  private static final int CALLS = 40;

  @Autowired private UsageRepository usage;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from household where name = 'Pg ledger'");
    jdbc.update("delete from app_user where google_sub = 'sub-pg-ledger'");
  }

  @Test
  void fortyConcurrentRecordsOnAnEmptyMonthLoseNothing() throws Exception {
    long household =
        households.create(HouseholdTestSupport.insertUser(jdbc, "pg-ledger"), "Pg ledger").id();
    var properties =
        new AiProperties(
            true,
            new BigDecimal("5.00"),
            new BigDecimal("1.65"),
            new BigDecimal("9.90"),
            768,
            Duration.ofSeconds(60),
            Duration.ofSeconds(120),
            "food-and-plan");
    var ledger =
        new UsageLedger(
            usage,
            properties,
            new MutableClock(Instant.parse("2026-11-10T10:00:00Z"), ClockConfig.ZONE),
            transactions);

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(CALLS);
    try {
      List<Future<?>> results = new ArrayList<>();
      for (int i = 0; i < CALLS; i++) {
        results.add(
            pool.submit(() -> {
              go.await();
              ledger.record(household, 1_000, 500);
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

    var total = ledger.usageThisMonth(household);
    assertThat(total.inputTokens()).isEqualTo(CALLS * 1_000L);
    assertThat(total.outputTokens()).isEqualTo(CALLS * 500L);
    assertThat(total.costMicroUsd()).isEqualTo(CALLS * 6_600L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from assistant_usage where household_id = ?",
                Long.class,
                household))
        .isEqualTo(1L);
  }
}
