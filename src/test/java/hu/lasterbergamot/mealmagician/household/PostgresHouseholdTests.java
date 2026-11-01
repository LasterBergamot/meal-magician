package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The household mappings and queries on the real database: Hibernate validates the entities against
 * the Postgres schema at startup, and the ordering and membership queries run on Postgres, not just
 * on H2.
 */
@Transactional
class PostgresHouseholdTests extends AbstractPostgresIntegrationTest {

  @Autowired private HouseholdService service;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void createListRenameAndTenantCheckWorkOnPostgres() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "pg-anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "pg-bela");
    long annas = service.create(anna, "beta").id();
    service.create(anna, "Alfa");

    assertThat(service.listFor(anna))
        .extracting(HouseholdSummary::name)
        .containsExactly("Alfa", "beta");

    service.rename(annas, anna, "Gamma");
    assertThat(service.require(annas, anna).name()).isEqualTo("Gamma");
    assertThat(service.find(annas, bela)).isEmpty();
  }
}
