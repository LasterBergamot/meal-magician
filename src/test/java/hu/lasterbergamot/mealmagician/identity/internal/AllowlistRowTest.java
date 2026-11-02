package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The two helpers {@code admin/_allowlist.jte} uses on a row: the badge tone and the id. */
class AllowlistRowTest {

  private static AllowlistRow row(String email, AllowlistOrigin origin) {
    return new AllowlistRow(email, origin.messageKey(), "", true);
  }

  @Test
  void aBootstrapAdminGetsTheBrandTone() {
    assertThat(row("a@example.com", AllowlistOrigin.BOOTSTRAP).badgeTone()).isEqualTo("brand");
  }

  @Test
  void aHouseholdInviteGetsTheInfoTone() {
    assertThat(row("a@example.com", AllowlistOrigin.INVITE).badgeTone()).isEqualTo("info");
  }

  @Test
  void anAddressAddedOnThePageGetsTheNeutralTone() {
    assertThat(row("a@example.com", AllowlistOrigin.ADMIN).badgeTone()).isEqualTo("neutral");
  }

  @Test
  void theIdIsTheSameForTheSameAddressInAnyCaseAndDiffersBetweenAddresses() {
    AllowlistRow anna = row("anna@example.com", AllowlistOrigin.ADMIN);

    assertThat(anna.domId())
        .isEqualTo(row("ANNA@example.com", AllowlistOrigin.ADMIN).domId())
        .isNotEqualTo(row("bela@example.com", AllowlistOrigin.ADMIN).domId())
        .matches("allowlist-[0-9a-f]+");
  }
}
