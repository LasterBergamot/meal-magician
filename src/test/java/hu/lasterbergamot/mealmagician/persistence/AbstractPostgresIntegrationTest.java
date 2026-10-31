package hu.lasterbergamot.mealmagician.persistence;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the real database: Postgres with pgvector, the source of truth
 * (PLAN.md D10). Extend it, and the Spring context is wired to a throw-away container.
 *
 * <p>One container is started for the whole test JVM and shared by every subclass, which is much
 * faster than one per class. Testcontainers' Ryuk sidecar removes it when the JVM exits. The image
 * is the same digest as compose.yml, so tests and local development run the same Postgres. Tagged
 * {@code integration}, so {@code task test:fast} skips these.
 */
@SpringBootTest
@ActiveProfiles({"dev", "postgres"})
@Tag("integration")
public abstract class AbstractPostgresIntegrationTest {

  // Digest only: Testcontainers rejects the `tag@digest` form that compose.yml uses.
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "pgvector/pgvector@sha256:ac08538c6f8b9904c33c8224c5e5706dbe760aca29db1d096972b4052c22a75d")
          .asCompatibleSubstituteFor("postgres");

  /** Started once; {@code @ServiceConnection} points the datasource at it, overriding the yml. */
  @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);

  static {
    POSTGRES.start();
  }
}
