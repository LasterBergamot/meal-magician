package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class AllowlistServiceTest {

  private final AllowedEmailRepository repository = mock(AllowedEmailRepository.class);
  private final AllowlistService allowlist =
      new AllowlistService(new IdentityProperties(List.of("Boss@Example.com")), repository);

  @Test
  void adminEmailIsAllowedWithoutADatabaseRowIgnoringCase() {
    assertThat(allowlist.isAllowed("BOSS@example.com")).isTrue();
    assertThat(allowlist.isAdmin("BOSS@example.com")).isTrue();
  }

  @Test
  void emailOnTheAllowlistTableIsAllowedButIsNotAnAdmin() {
    when(repository.existsById("guest@example.com")).thenReturn(true);

    assertThat(allowlist.isAllowed("Guest@Example.com")).isTrue();
    assertThat(allowlist.isAdmin("Guest@Example.com")).isFalse();
  }

  @Test
  void unknownEmailIsRefused() {
    assertThat(allowlist.isAllowed("stranger@example.com")).isFalse();
  }
}
