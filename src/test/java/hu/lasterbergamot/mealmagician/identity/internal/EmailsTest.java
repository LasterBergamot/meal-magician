package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailsTest {

  @Test
  void lowerCasesAndTrims() {
    assertThat(Emails.normalize("  Alice@Gmail.COM ")).isEqualTo("alice@gmail.com");
  }

  @Test
  void usesRootLocaleSoTurkishDottedIDoesNotBreakMatching() {
    var previous = java.util.Locale.getDefault();
    java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
    try {
      assertThat(Emails.normalize("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
    } finally {
      java.util.Locale.setDefault(previous);
    }
  }

  @Test
  void treatsNullAsEmpty() {
    assertThat(Emails.normalize(null)).isEmpty();
  }
}
