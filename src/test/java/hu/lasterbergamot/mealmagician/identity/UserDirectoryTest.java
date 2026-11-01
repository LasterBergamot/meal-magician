package hu.lasterbergamot.mealmagician.identity;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The directory against the real schema: ids in, names and addresses out, nothing else. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class UserDirectoryTest {

  @Autowired private UserDirectory directory;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void returnsTheProfileOfEachKnownId() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "anna");
    long bela = HouseholdTestSupport.insertUser(jdbc, "bela");

    assertThat(directory.find(List.of(anna, bela)))
        .containsOnlyKeys(anna, bela)
        .containsEntry(anna, new UserProfile(anna, "anna", "anna@example.com"))
        .containsEntry(bela, new UserProfile(bela, "bela", "bela@example.com"));
  }

  @Test
  void leavesAnUnknownIdOutOfTheResult() {
    long anna = HouseholdTestSupport.insertUser(jdbc, "anna");

    assertThat(directory.find(List.of(anna, anna + 1000))).containsOnlyKeys(anna);
    assertThat(directory.find(List.of())).isEmpty();
  }
}
