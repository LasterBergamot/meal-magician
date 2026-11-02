package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ThemeTest {

  @ParameterizedTest
  @CsvSource({"light, LIGHT", "dark, DARK", "system, SYSTEM"})
  void aKnownCookieValueIsRead(String value, Theme expected) {
    assertThat(Theme.fromCookie(value)).isEqualTo(expected);
    assertThat(expected.value()).isEqualTo(value);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"purple", "DARK", "Light", " dark", "dark;x=1", "<script>"})
  void anythingElseMeansSystem(String value) {
    assertThat(Theme.fromCookie(value)).isEqualTo(Theme.SYSTEM);
  }

  @Test
  void systemLeavesEachThemeColorToItsOwnColorScheme() {
    assertThat(Theme.SYSTEM.themeColorMedia(Theme.LIGHT))
        .isEqualTo("(prefers-color-scheme: light)");
    assertThat(Theme.SYSTEM.themeColorMedia(Theme.DARK)).isEqualTo("(prefers-color-scheme: dark)");
  }

  @Test
  void anExplicitChoiceSwitchesTheOtherThemeColorOff() {
    assertThat(Theme.LIGHT.themeColorMedia(Theme.LIGHT)).isEqualTo("all");
    assertThat(Theme.LIGHT.themeColorMedia(Theme.DARK)).isEqualTo("not all");
    assertThat(Theme.DARK.themeColorMedia(Theme.DARK)).isEqualTo("all");
    assertThat(Theme.DARK.themeColorMedia(Theme.LIGHT)).isEqualTo("not all");
  }
}
