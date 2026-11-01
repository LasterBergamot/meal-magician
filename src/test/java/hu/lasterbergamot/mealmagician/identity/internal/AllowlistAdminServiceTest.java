package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class AllowlistAdminServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

  private final AllowedEmailRepository repository = mock(AllowedEmailRepository.class);
  private final AppUserRepository users = mock(AppUserRepository.class);
  private final FindByIndexNameSessionRepository<Session> sessions =
      mock(FindByIndexNameSessionRepository.class);
  private final AllowlistAdminService service =
      new AllowlistAdminService(
          new IdentityProperties(List.of("Zed@Example.com", "boss@example.com")),
          repository,
          users,
          sessions,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static AppUser user(String googleSub, String email) {
    return new AppUser(googleSub, email, "Name", NOW);
  }

  private static AllowedEmail row(String email, AllowedEmailSource source) {
    return AllowedEmail.builder()
        .email(email)
        .source(source)
        .addedBy(1L)
        .createdAt(NOW.minusSeconds(60))
        .build();
  }

  @Test
  void listsBootstrapAdminsFirstThenTheRowsAndShowsAnAdminWithARowOnce() {
    when(repository.findAll(Sort.by("email")))
        .thenReturn(
            List.of(
                row("anna@example.com", AllowedEmailSource.INVITE),
                row("boss@example.com", AllowedEmailSource.INVITE),
                row("carl@example.com", AllowedEmailSource.ADMIN)));

    List<AllowlistEntry> entries = service.list();

    assertThat(entries)
        .extracting(AllowlistEntry::email, AllowlistEntry::origin)
        .containsExactly(
            tuple("boss@example.com", AllowlistOrigin.BOOTSTRAP),
            tuple("zed@example.com", AllowlistOrigin.BOOTSTRAP),
            tuple("anna@example.com", AllowlistOrigin.INVITE),
            tuple("carl@example.com", AllowlistOrigin.ADMIN));
    assertThat(entries)
        .extracting(AllowlistEntry::removable)
        .containsExactly(false, false, true, true);
    assertThat(entries.get(0).addedAt()).isNull();
    assertThat(entries.get(3).addedAt()).isEqualTo(NOW.minusSeconds(60));
  }

  @Test
  void addsANormalizedAddressWithTheAdminSource() {
    service.add("  New@Example.COM ", 7L);

    ArgumentCaptor<AllowedEmail> saved = ArgumentCaptor.forClass(AllowedEmail.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
    assertThat(saved.getValue().getSource()).isEqualTo(AllowedEmailSource.ADMIN);
    assertThat(saved.getValue().getAddedBy()).isEqualTo(7L);
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void refusesAnAddressThatIsNotShapedLikeOne() {
    assertThatThrownBy(() -> service.add("not-an-email", 7L))
        .isInstanceOfSatisfying(
            AllowlistRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(AllowlistProblem.INVALID_EMAIL));
    assertThatThrownBy(() -> service.add("", 7L)).isInstanceOf(AllowlistRefusedException.class);
    assertThatThrownBy(() -> service.add("a".repeat(250) + "@b.co", 7L))
        .isInstanceOf(AllowlistRefusedException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void refusesAnAddressThatIsAlreadyOnTheTable() {
    when(repository.existsById("anna@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.add("Anna@example.com", 7L))
        .isInstanceOfSatisfying(
            AllowlistRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(AllowlistProblem.ALREADY_ALLOWED));
    verify(repository, never()).save(any());
  }

  @Test
  void refusesABootstrapAdminAsAlreadyAllowed() {
    assertThatThrownBy(() -> service.add("BOSS@example.com", 7L))
        .isInstanceOfSatisfying(
            AllowlistRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(AllowlistProblem.ALREADY_ALLOWED));
  }

  @Test
  void removesTheNormalizedAddress() {
    service.remove(" Anna@Example.com ");

    verify(repository).deleteById("anna@example.com");
  }

  @Test
  void neverRemovesABootstrapAdmin() {
    assertThatThrownBy(() -> service.remove("Boss@example.com"))
        .isInstanceOfSatisfying(
            AllowlistRefusedException.class,
            e -> assertThat(e.problem()).isEqualTo(AllowlistProblem.BOOTSTRAP_ADMIN));
    verify(repository, never()).deleteById(any());
    verify(sessions, never()).deleteById(any());
  }

  @Test
  void removingAnAddressEndsTheLiveSessionsOfThatPerson() {
    when(users.findAllByEmail("anna@example.com"))
        .thenReturn(List.of(user("sub-anna", "anna@example.com")));
    when(sessions.findByPrincipalName("sub-anna"))
        .thenReturn(Map.of("anna-laptop", mock(Session.class), "anna-phone", mock(Session.class)));

    service.remove("Anna@example.com");

    verify(repository).deleteById("anna@example.com");
    verify(sessions).deleteById("anna-laptop");
    verify(sessions).deleteById("anna-phone");
  }

  @Test
  void removingAnAddressLeavesOtherPeoplesSessionsAlone() {
    when(users.findAllByEmail("anna@example.com"))
        .thenReturn(List.of(user("sub-anna", "anna@example.com")));
    when(sessions.findByPrincipalName("sub-anna"))
        .thenReturn(Map.of("anna-phone", mock(Session.class)));

    service.remove("anna@example.com");

    verify(sessions).findByPrincipalName("sub-anna");
    verify(sessions, never()).findByPrincipalName("sub-bela");
    verify(sessions, never()).deleteById("bela-phone");
    verify(sessions).deleteById("anna-phone");
  }

  @Test
  void removingAnAddressNobodyHasSignedInWithEndsNoSessions() {
    when(users.findAllByEmail("ghost@example.com")).thenReturn(List.of());

    service.remove("ghost@example.com");

    verify(repository).deleteById("ghost@example.com");
    verify(sessions, never()).findByPrincipalName(any());
    verify(sessions, never()).deleteById(any());
  }

  @Test
  void endsTheSessionsOfEveryAccountThatLastSignedInWithTheAddress() {
    when(users.findAllByEmail("shared@example.com"))
        .thenReturn(
            List.of(user("sub-old", "shared@example.com"), user("sub-new", "shared@example.com")));
    when(sessions.findByPrincipalName("sub-old")).thenReturn(Map.of("old-1", mock(Session.class)));
    when(sessions.findByPrincipalName("sub-new")).thenReturn(Map.of("new-1", mock(Session.class)));

    service.remove("shared@example.com");

    verify(sessions).deleteById("old-1");
    verify(sessions).deleteById("new-1");
  }
}
