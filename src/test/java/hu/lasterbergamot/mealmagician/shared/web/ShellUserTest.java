package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ShellUserTest {

  @ParameterizedTest
  @CsvSource({
    "Török András, TA",
    "Anna, A",
    "Anna Maria Kovács, AM",
    "  bela   nagy  , BN",
    "élő Ádám, ÉÁ"
  })
  void initialsAreTheFirstLettersOfTheFirstTwoWords(String name, String initials) {
    assertThat(new ShellUser(name).initials()).isEqualTo(initials);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void aNameWithoutLettersGivesAQuestionMark(String name) {
    assertThat(new ShellUser(name).initials()).isEqualTo("?");
    assertThat(new ShellUser(name).displayName()).isEmpty();
  }
}
