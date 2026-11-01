package hu.lasterbergamot.mealmagician.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.identity.internal.AllowedEmail;
import hu.lasterbergamot.mealmagician.identity.internal.AllowedEmailRepository;
import hu.lasterbergamot.mealmagician.identity.internal.AllowedEmailSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AllowlistEditorTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final AllowedEmailRepository repository = mock(AllowedEmailRepository.class);
  private final AllowlistEditor editor =
      new AllowlistEditor(repository, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void normalizesToTrimmedLowerCase() {
    assertThat(editor.normalize("  Guest@Example.COM ")).isEqualTo("guest@example.com");
    assertThat(editor.normalize(null)).isEmpty();
  }

  @Test
  void addsAnInvitedAddressWithTheInviteSource() {
    editor.allowInvitedAddress("Guest@Example.com", 7L);

    ArgumentCaptor<AllowedEmail> saved = ArgumentCaptor.forClass(AllowedEmail.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("guest@example.com");
    assertThat(saved.getValue().getSource()).isEqualTo(AllowedEmailSource.INVITE);
    assertThat(saved.getValue().getAddedBy()).isEqualTo(7L);
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void keepsAnExistingEntryUntouched() {
    when(repository.existsById("guest@example.com")).thenReturn(true);

    editor.allowInvitedAddress("GUEST@example.com", 7L);

    verify(repository, never()).save(any());
  }
}
