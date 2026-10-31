package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Runs against the dev profile's H2, so the entity mapping is checked against the real schema. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AppUserServiceTest {

  @Autowired private AppUserService service;
  @Autowired private AppUserRepository users;
  @Autowired private AllowedEmailRepository allowedEmails;

  @Test
  void firstSignInCreatesTheUserWithTheDefaultLocale() {
    var user = service.recordSignIn("sub-new", "new@x.hu", "New Person");

    assertThat(user.getId()).isNotNull();
    assertThat(user.getLocale()).isEqualTo("hu");
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(users.findByGoogleSub("sub-new")).isPresent();
  }

  @Test
  void laterSignInsRefreshEmailAndNameWithoutCreatingAnotherRow() {
    var first = service.recordSignIn("sub-same", "old@x.hu", "Old Name");
    var second = service.recordSignIn("sub-same", "renamed@x.hu", "New Name");

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getEmail()).isEqualTo("renamed@x.hu");
    assertThat(second.getDisplayName()).isEqualTo("New Name");
    assertThat(users.findAll().stream().filter(u -> u.getGoogleSub().equals("sub-same")))
        .hasSize(1);
  }

  @Test
  void allowlistEntriesRoundTripThroughTheDatabase() {
    allowedEmails.saveAndFlush(
        AllowedEmail.builder()
            .email("guest@x.hu")
            .source(AllowedEmailSource.INVITE)
            .createdAt(java.time.Instant.now())
            .build());

    var saved = allowedEmails.findById("guest@x.hu").orElseThrow();
    assertThat(saved.getSource()).isEqualTo(AllowedEmailSource.INVITE);
    assertThat(saved.getAddedBy()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }
}
