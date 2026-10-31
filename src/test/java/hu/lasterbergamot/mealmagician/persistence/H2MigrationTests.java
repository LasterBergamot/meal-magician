package hu.lasterbergamot.mealmagician.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The `dev` profile: H2 in PostgreSQL mode, only the portable migrations, a shrinkable pool. */
@SpringBootTest
@ActiveProfiles("dev")
class H2MigrationTests {

  @Autowired private Flyway flyway;
  @Autowired private DataSource dataSource;

  @Test
  void appliesOnlyThePortableMigrations() {
    assertThat(flyway.info().applied())
        .extracting(migration -> migration.getVersion().getVersion())
        .containsExactly("1", "3");
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void runsOnH2InPostgresMode() throws Exception {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isEqualTo("PostgreSQL");
    }
  }

  @Test
  void poolMayShrinkToZeroWhenIdle() {
    var pool = (HikariDataSource) dataSource;
    assertThat(pool.getMinimumIdle()).isZero();
    assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
  }
}
