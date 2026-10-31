package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityPropertiesTest {

  @Test
  void normalizesAndDropsBlankAdminEmails() {
    var properties =
        new IdentityProperties(Arrays.asList(" Admin@Example.com", "", "  ", "b@x.hu"));

    assertThat(properties.normalizedAdminEmails())
        .containsExactlyInAnyOrder("admin@example.com", "b@x.hu");
  }

  @Test
  void noAdminEmailsMeansEmptySet() {
    assertThat(new IdentityProperties(null).normalizedAdminEmails()).isEmpty();
    assertThat(new IdentityProperties(List.of()).normalizedAdminEmails()).isEmpty();
  }
}
