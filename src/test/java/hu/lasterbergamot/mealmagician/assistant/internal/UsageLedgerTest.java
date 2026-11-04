package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.assistant.MonthlyUsage;
import hu.lasterbergamot.mealmagician.assistant.SpendCapExceededException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.shared.ClockConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The spend cap on the real schema (H2 here; the concurrent case runs on Postgres in {@link
 * PostgresUsageLedgerTests}). Not {@code @Transactional}: the ledger writes in transactions of its
 * own, so the households must be committed, and the test removes them itself.
 */
@SpringBootTest
@ActiveProfiles("dev")
class UsageLedgerTest {

  /** 11:30 on 2026-11-30, Budapest: the last day of a month. */
  private static final Instant END_OF_NOVEMBER = Instant.parse("2026-11-30T10:30:00Z");

  @Autowired private UsageRepository usage;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;

  private final MutableClock clock = new MutableClock(END_OF_NOVEMBER, ClockConfig.ZONE);
  private long annas;
  private long cilis;

  @BeforeEach
  void households() {
    annas =
        households.create(HouseholdTestSupport.insertUser(jdbc, "ledger-anna"), "Ledger A").id();
    cilis =
        households.create(HouseholdTestSupport.insertUser(jdbc, "ledger-cili"), "Ledger C").id();
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from household where name in ('Ledger A', 'Ledger C')");
    jdbc.update("delete from app_user where google_sub in ('sub-ledger-anna', 'sub-ledger-cili')");
  }

  /** Prices of 1.65 and 9.90 USD per million tokens; the cap is given in USD. */
  private UsageLedger ledger(String capUsd) {
    var properties =
        new AiProperties(
            true,
            new BigDecimal(capUsd),
            new BigDecimal("1.65"),
            new BigDecimal("9.90"),
            768,
            Duration.ofSeconds(60),
            Duration.ofSeconds(120),
            "food-and-plan");
    return new UsageLedger(usage, properties, clock, transactions);
  }

  @Test
  void aHouseholdWithNoRowIsWithinTheCapAndHasUsedNothing() {
    var ledger = ledger("5.00");

    assertThatCode(() -> ledger.assertWithinCap(annas)).doesNotThrowAnyException();
    assertThat(ledger.usageThisMonth(annas))
        .isEqualTo(new MonthlyUsage(LocalDate.of(2026, 11, 1), 0, 0, 0, 5_000_000L));
  }

  @Test
  void recordAddsTheTokensAndTheComputedCost() {
    var ledger = ledger("5.00");

    long cost = ledger.record(annas, 1_000, 500);
    ledger.record(annas, 1_000, 500);

    assertThat(cost).isEqualTo(6_600L);
    assertThat(ledger.usageThisMonth(annas))
        .isEqualTo(new MonthlyUsage(LocalDate.of(2026, 11, 1), 2_000, 1_000, 13_200L, 5_000_000L));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from assistant_usage where household_id = ?", Long.class, annas))
        .isEqualTo(1L);
  }

  @Test
  void aHouseholdAtTheCapIsRefusedAndOneBelowIsNot() {
    // Cap 0.0132 USD = 13 200 micro-USD; one call costs 6 600.
    var ledger = ledger("0.0132");
    ledger.record(annas, 1_000, 500);
    assertThatCode(() -> ledger.assertWithinCap(annas)).doesNotThrowAnyException();

    ledger.record(annas, 1_000, 500);

    assertThatThrownBy(() -> ledger.assertWithinCap(annas))
        .isInstanceOf(SpendCapExceededException.class)
        .satisfies(e -> {
          var refused = (SpendCapExceededException) e;
          assertThat(refused.usedMicroUsd()).isEqualTo(13_200L);
          assertThat(refused.capMicroUsd()).isEqualTo(13_200L);
        });
  }

  @Test
  void aCapOfZeroBlocksEveryone() {
    assertThatThrownBy(() -> ledger("0").assertWithinCap(annas))
        .isInstanceOf(SpendCapExceededException.class);
  }

  @Test
  void twoHouseholdsNeverSeeEachOthersUsage() {
    var ledger = ledger("0.0066");
    ledger.record(annas, 1_000, 500);

    assertThat(ledger.usageThisMonth(cilis).costMicroUsd()).isZero();
    assertThatCode(() -> ledger.assertWithinCap(cilis)).doesNotThrowAnyException();
    assertThatThrownBy(() -> ledger.assertWithinCap(annas))
        .isInstanceOf(SpendCapExceededException.class);
  }

  @Test
  void aNewMonthStartsAtZero() {
    var ledger = ledger("0.0066");
    ledger.record(annas, 1_000, 500);
    assertThatThrownBy(() -> ledger.assertWithinCap(annas))
        .isInstanceOf(SpendCapExceededException.class);

    // 00:30 on 1 December in Budapest is still 30 November in UTC: the Budapest clock decides.
    clock.set(Instant.parse("2026-11-30T23:30:00Z"));

    assertThatCode(() -> ledger.assertWithinCap(annas)).doesNotThrowAnyException();
    assertThat(ledger.usageThisMonth(annas))
        .isEqualTo(new MonthlyUsage(LocalDate.of(2026, 12, 1), 0, 0, 0, 6_600L));
    // and November is still on record
    clock.set(END_OF_NOVEMBER);
    assertThat(ledger.usageThisMonth(annas).costMicroUsd()).isEqualTo(6_600L);
  }

  @Test
  void costIsRoundedUpPerCall() {
    var ledger = ledger("5.00");

    // 1 input token at 1.65 USD per million is 1.65 micro-USD: charged as 2.
    assertThat(ledger.record(annas, 1, 0)).isEqualTo(2L);
    assertThat(ledger.record(annas, 0, 0)).isZero();
    assertThat(ledger.usageThisMonth(annas).costMicroUsd()).isEqualTo(2L);
  }

  @Test
  void aRecordedCallSurvivesARollbackOfTheCallersTransaction() {
    var ledger = ledger("5.00");
    var callerTransaction =
        new org.springframework.transaction.support.TransactionTemplate(transactions);

    assertThatThrownBy(() ->
            callerTransaction.executeWithoutResult(status -> {
              ledger.record(annas, 1_000, 500);
              throw new IllegalStateException("the caller fails afterwards");
            }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(ledger.usageThisMonth(annas).costMicroUsd()).isEqualTo(6_600L);
  }
}
