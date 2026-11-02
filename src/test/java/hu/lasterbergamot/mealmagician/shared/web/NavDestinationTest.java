package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NavDestinationTest {

  @Test
  void onlyDestinationsWhosePagesExistAreShown() {
    assertThat(NavDestination.visible())
        .containsExactly(NavDestination.PLAN, NavDestination.MEALS, NavDestination.HOUSEHOLD);
  }

  @ParameterizedTest
  @CsvSource({
    "/, PLAN",
    "/households, HOUSEHOLD",
    "/households/members, HOUSEHOLD",
    "/households/7/switch, HOUSEHOLD",
    "/meals, MEALS",
    "/occasions/3, OCCASIONS"
  })
  void aPathBelongsToItsDestinationAndThePagesBelowIt(String path, NavDestination destination) {
    assertThat(NavDestination.matching(path)).isEqualTo(destination);
  }

  @ParameterizedTest
  @CsvSource({"/login", "/join/abc", "/households-old", "/mealsx"})
  void aPathOutsideEveryDestinationHighlightsNothing(String path) {
    assertThat(NavDestination.matching(path)).isNull();
  }

  @Test
  void everyLabelExistsInBothLanguages() throws IOException {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    for (NavDestination destination : NavDestination.values()) {
      assertThat(hu.getProperty(destination.labelKey()))
          .as("hu label of %s", destination)
          .isNotBlank();
      assertThat(en.getProperty(destination.labelKey()))
          .as("en label of %s", destination)
          .isNotBlank();
    }
    assertThat(List.of(NavDestination.values())).hasSize(4);
  }

  private static Properties bundle(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = NavDestinationTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("bundle %s on the classpath", name).isNotNull();
      properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    }
    return properties;
  }
}
