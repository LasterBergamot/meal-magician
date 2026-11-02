package hu.lasterbergamot.mealmagician.household.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The avatar initials of a member row follow the same rule as the app shell's avatar. */
class MemberRowTest {

  private static MemberRow named(String name) {
    return new MemberRow(1, name, "x@example.com", false, false, false);
  }

  @Test
  void initialsAreTheFirstLettersOfTheFirstTwoWords() {
    assertThat(named("Kovács Anna Mária").initials()).isEqualTo("KA");
  }

  @Test
  void aSingleWordGivesOneLetter() {
    assertThat(named("anna").initials()).isEqualTo("A");
  }

  @Test
  void aBlankNameGivesAQuestionMark() {
    assertThat(named("  ").initials()).isEqualTo("?");
  }
}
