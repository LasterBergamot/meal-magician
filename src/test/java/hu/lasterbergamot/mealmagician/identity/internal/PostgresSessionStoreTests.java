package hu.lasterbergamot.mealmagician.identity.internal;

import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/** Same round trip as on H2, on the real database where sessions must survive a restart. */
class PostgresSessionStoreTests extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcIndexedSessionRepository sessions;

  @Test
  void signedInUserSurvivesAWriteAndReadThroughPostgres() {
    SessionRoundTrip.verify(sessions);
  }
}
