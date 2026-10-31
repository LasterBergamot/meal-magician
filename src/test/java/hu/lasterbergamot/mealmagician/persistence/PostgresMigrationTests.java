package hu.lasterbergamot.mealmagician.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** `task migrate-check` runs this: every migration applies to a real Postgres and validates. */
class PostgresMigrationTests extends AbstractPostgresIntegrationTest {

  @Autowired private Flyway flyway;
  @Autowired private DataSource dataSource;

  @Test
  void appliesPortableAndPostgresOnlyMigrations() {
    assertThat(flyway.info().applied())
        .extracting(migration -> migration.getVersion().getVersion())
        .containsExactly("1", "2");
    assertThat(flyway.info().pending()).isEmpty();
    flyway.validate();
  }

  @Test
  void pgvectorExtensionIsInstalled() throws Exception {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var result =
            statement.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'vector'")) {
      assertThat(result.next()).isTrue();
    }
  }
}
