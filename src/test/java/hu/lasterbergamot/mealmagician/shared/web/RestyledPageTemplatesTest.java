package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The sign-in, error, hello and allowlist templates (plan-ui-redesign.md R5a and R5c) are built
 * from the design tokens and the {@code ui/} components only: no leftover {@code brand-700} or
 * {@code stone-*} class from the old skeleton. The old scale is deleted for good in R5d, which
 * makes this a repo-wide guard; until then each restyled page area pins itself here.
 */
class RestyledPageTemplatesTest {

  private static final List<Path> FOLDERS =
      List.of(
          Path.of("src/main/jte/login"),
          Path.of("src/main/jte/error"),
          Path.of("src/main/jte/hello"),
          Path.of("src/main/jte/admin"));

  @Test
  void restyledTemplatesUseNoOldPaletteClass() throws IOException {
    int checked = 0;
    for (Path folder : FOLDERS) {
      try (Stream<Path> files = Files.list(folder)) {
        for (Path template : files.toList()) {
          String source = Files.readString(template, StandardCharsets.UTF_8);

          assertThat(source)
              .as(template.toString())
              .doesNotContainPattern("(?<![\\w-])(brand-\\d|stone-|red-\\d|green-\\d|white\\b)");
          checked++;
        }
      }
    }

    assertThat(checked).as("templates found").isEqualTo(7);
  }
}
