package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The font fallback chain of the headings (plan-ui-redesign.md UI-D4, #71). Headings are Fredoka,
 * which has no Latin Extended-A glyphs, so Hungarian ő and ű must come from Nunito, the next family
 * in {@code --font-display}. The browser decides per character from the {@code @font-face} {@code
 * unicode-range} rules, so this test reads {@code app.css} and checks that:
 *
 * <ul>
 *   <li>the stack is Fredoka, then Nunito, and names no web font that is not hosted (an unhosted
 *       name such as "Baloo 2" silently falls through to a system face);
 *   <li>no Fredoka face claims ő or ű, and a Nunito face does, at both heading weights (600, 700);
 *   <li>every face points to a file that exists.
 * </ul>
 *
 * The end-to-end test {@code FontFallbackE2ETest} proves the same in a real browser; this one is
 * the fast guard. The checker takes the stylesheet text, so the tests can also feed it a bad one.
 */
class FontFallbackTest {

  private static final Path STYLESHEET = Path.of("src/main/css/app.css");
  private static final Path STATIC_FILES = Path.of("src/main/resources/static");

  private static final Pattern DISPLAY_STACK = Pattern.compile("--font-display:\\s*([^;]+);");
  private static final Pattern FONT_FACE = Pattern.compile("@font-face\\s*\\{([^}]*)}");
  private static final Pattern FAMILY = Pattern.compile("font-family:\\s*\"([^\"]+)\"");
  private static final Pattern SOURCE = Pattern.compile("url\\(\"([^\"]+)\"\\)");
  private static final Pattern WEIGHTS = Pattern.compile("font-weight:\\s*(\\d+)\\s+(\\d+)");
  private static final Pattern UNICODE_RANGE = Pattern.compile("unicode-range:\\s*([^;]+);");
  private static final Pattern RANGE = Pattern.compile("U\\+([0-9A-Fa-f]+)(?:-([0-9A-Fa-f]+))?");

  /**
   * Families the user's device provides: one named face and the CSS generic families. They are not
   * web fonts, so they need no {@code @font-face}.
   */
  private static final List<String> SYSTEM_FACES =
      List.of("Trebuchet MS", "system-ui", "sans-serif", "serif");

  /** The letters Hungarian needs that Fredoka lacks: Ő, ő, Ű, ű. */
  private static final List<Integer> HUNGARIAN_DOUBLE_ACUTE =
      List.of(0x0150, 0x0151, 0x0170, 0x0171);

  private static final List<Integer> HEADING_WEIGHTS = List.of(600, 700);

  @Test
  void headingsFallBackFromFredokaToNunitoBeforeAnySystemFace() {
    String css = stylesheet();

    assertThat(displayStack(css).subList(0, 2)).containsExactly("Fredoka", "Nunito");
    assertThat(problems(css)).isEmpty();
  }

  @Test
  void onlyNunitoDrawsTheHungarianDoubleAcuteLetters() {
    List<Face> faces = faces(stylesheet());

    for (int letter : HUNGARIAN_DOUBLE_ACUTE) {
      assertThat(faces)
          .filteredOn(face -> face.family().equals("Fredoka") && face.covers(letter))
          .as("Fredoka faces claiming U+%04X", letter)
          .isEmpty();
      assertThat(faces)
          .filteredOn(face -> face.family().equals("Nunito") && face.covers(letter))
          .as("Nunito faces covering U+%04X", letter)
          .isNotEmpty();
    }
  }

  @Test
  void checkerFailsWhenTheStackNamesAFontThatIsNotHosted() {
    String bad =
        stylesheet().replace("\"Nunito\", \"Trebuchet MS\"", "\"Baloo 2\", \"Trebuchet MS\"");

    assertThat(problems(bad))
        .anySatisfy(message -> assertThat(message).contains("Baloo 2"))
        .anySatisfy(message -> assertThat(message).contains("Fredoka, then Nunito"));
  }

  @Test
  void checkerFailsWhenNoNunitoFaceCoversTheLetters() {
    String bad = stylesheet().replace("U+0100-02BA", "U+0100-014F");

    assertThat(problems(bad)).anySatisfy(message -> assertThat(message).contains("U+0150"));
  }

  @Test
  void checkerFailsWhenFredokaClaimsTheLetters() {
    String bad = stylesheet().replace("U+0000-00FF, U+0131", "U+0000-017F, U+0131");

    assertThat(problems(bad))
        .anySatisfy(message -> assertThat(message).contains("Fredoka").contains("U+0150"));
  }

  @Test
  void checkerFailsWhenAFaceFileIsMissing() {
    String bad = stylesheet().replace("nunito-latin-ext.woff2", "no-such-file.woff2");

    assertThat(problems(bad))
        .anySatisfy(message -> assertThat(message).contains("no-such-file.woff2"));
  }

  // ---- the checker ----

  /** Everything wrong with the heading font chain, as readable messages (empty when it is fine). */
  static List<String> problems(String css) {
    List<String> problems = new ArrayList<>();
    List<String> stack = displayStack(css);
    List<Face> faces = faces(css);

    if (stack.size() < 2 || !stack.get(0).equals("Fredoka") || !stack.get(1).equals("Nunito")) {
      problems.add("--font-display must start with Fredoka, then Nunito, but is " + stack);
    }
    for (String family : stack) {
      boolean hosted = faces.stream().anyMatch(face -> face.family().equals(family));
      if (!hosted && !SYSTEM_FACES.contains(family)) {
        problems.add(
            "--font-display names \"" + family + "\", which has no @font-face (not hosted)");
      }
    }
    for (int letter : HUNGARIAN_DOUBLE_ACUTE) {
      if (faces.stream().anyMatch(face -> face.family().equals("Fredoka") && face.covers(letter))) {
        problems.add(
            "A Fredoka face claims U+%04X, which Fredoka has no glyph for".formatted(letter));
      }
      for (int weight : HEADING_WEIGHTS) {
        boolean drawn =
            faces.stream()
                .anyMatch(face ->
                    face.family().equals("Nunito")
                        && face.covers(letter)
                        && face.minWeight() <= weight
                        && weight <= face.maxWeight());
        if (!drawn) {
          problems.add("No Nunito face covers U+%04X at weight %d".formatted(letter, weight));
        }
      }
    }
    for (Face face : faces) {
      if (!Files.isRegularFile(STATIC_FILES.resolve(face.file().substring(1)))) {
        problems.add(face.family() + " points to " + face.file() + ", which does not exist");
      }
    }
    return problems;
  }

  /** The families of {@code --font-display} in order, without quotes (generics keep their name). */
  static List<String> displayStack(String css) {
    Matcher stack = DISPLAY_STACK.matcher(css);
    if (!stack.find()) {
      throw new IllegalStateException("--font-display is not declared in app.css");
    }
    List<String> families = new ArrayList<>();
    for (String family : stack.group(1).split(",")) {
      families.add(family.strip().replace("\"", ""));
    }
    return families;
  }

  /** The {@code @font-face} rules of the stylesheet. */
  static List<Face> faces(String css) {
    List<Face> faces = new ArrayList<>();
    Matcher block = FONT_FACE.matcher(css);
    while (block.find()) {
      String rule = block.group(1);
      Matcher family = FAMILY.matcher(rule);
      Matcher source = SOURCE.matcher(rule);
      Matcher weights = WEIGHTS.matcher(rule);
      Matcher range = UNICODE_RANGE.matcher(rule);
      if (!family.find() || !source.find() || !weights.find() || !range.find()) {
        throw new IllegalStateException("Unreadable @font-face rule: " + rule);
      }
      List<int[]> ranges = new ArrayList<>();
      Matcher part = RANGE.matcher(range.group(1));
      while (part.find()) {
        int from = Integer.parseInt(part.group(1), 16);
        int to = part.group(2) == null ? from : Integer.parseInt(part.group(2), 16);
        ranges.add(new int[] {from, to});
      }
      faces.add(
          new Face(
              family.group(1),
              source.group(1),
              Integer.parseInt(weights.group(1)),
              Integer.parseInt(weights.group(2)),
              ranges));
    }
    return faces;
  }

  /**
   * One {@code @font-face}: which family, file and weights it declares and the code points it
   * covers.
   */
  record Face(String family, String file, int minWeight, int maxWeight, List<int[]> ranges) {
    boolean covers(int codePoint) {
      return ranges.stream().anyMatch(range -> range[0] <= codePoint && codePoint <= range[1]);
    }
  }

  private static String stylesheet() {
    try {
      return Files.readString(STYLESHEET, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
