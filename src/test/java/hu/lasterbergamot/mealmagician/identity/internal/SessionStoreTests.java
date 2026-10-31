package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/** Sessions are stored in the database, and nothing polls it on a timer. */
@SpringBootTest
@ActiveProfiles("dev")
class SessionStoreTests {

  @Autowired private JdbcIndexedSessionRepository sessions;

  @Test
  void signedInUserSurvivesAWriteAndReadThroughTheSessionTable() {
    SessionRoundTrip.verify(sessions);
  }

  @Test
  void noCleanupTimerIsScheduledSoRailwayCanSleep() {
    assertThat(ReflectionTestUtils.getField(sessions, "taskScheduler")).isNull();
  }
}
