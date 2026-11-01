package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The colour contrast gate (plan-ui-redesign.md §6.4, Appendix C; UI-D10). It reads the design
 * tokens from {@code src/main/css/app.css} and checks every foreground-on-background pair of
 * Appendix C in both themes (Moonlight, the light default, and Midnight, the dark one), using the
 * WCAG 2 contrast ratio. Text pairs need 4.5, marks and borders 3, and the slot marks on their own
 * soft tile 2.5.
 *
 * <p>The checker is a set of static methods that take the stylesheet text, so the tests can also
 * feed it a deliberately bad token and check that it fails. A token that drifts below its minimum
 * fails the build, not a reviewer's eye.
 */
class ContrastTokensTest {

  private static final Path STYLESHEET = Path.of("src/main/css/app.css");

  /** The Moonlight (light) block: the {@code :root} rule at the start of a line. */
  private static final Pattern MOONLIGHT = Pattern.compile("(?m)^:root\\s*\\{([^}]*)}");

  /** The Midnight (dark) block, chosen explicitly. */
  private static final Pattern MIDNIGHT =
      Pattern.compile("(?m)^:root\\[data-theme=\"dark\"]\\s*\\{([^}]*)}");

  /** The Midnight block that applies when the user has not chosen a theme (the "system" mode). */
  private static final Pattern SYSTEM_DARK =
      Pattern.compile(
          ":root:not\\(\\[data-theme=\"light\"]\\):not\\(\\[data-theme=\"dark\"]\\)\\s*\\{([^}]*)}");

  /** One {@code --mm-name: #rrggbb;} declaration; an alpha pair after the six digits is ignored. */
  private static final Pattern TOKEN =
      Pattern.compile("--mm-([a-z0-9-]+):\\s*#([0-9a-fA-F]{6})(?:[0-9a-fA-F]{2})?;");

  /**
   * Appendix C, one pair per line as {@code foreground background minimum}. Every pair is checked
   * in both themes. The table in the plan is the reference; keep the two in step.
   */
  private static final String PAIRS =
      """
      ink surface 4.5
      ink-muted surface 4.5
      border-strong surface 3
      outline surface 3
      focus-ring surface 3
      danger surface 4.5
      ink surface-raised 4.5
      ink-muted surface-raised 4.5
      border-strong surface-raised 3
      outline surface-raised 3
      focus-ring surface-raised 3
      danger surface-raised 4.5
      ink surface-sunken 4.5
      ink-muted surface-sunken 4.5
      border-strong surface-sunken 3
      outline surface-sunken 3
      focus-ring surface-sunken 3
      danger surface-sunken 4.5
      brand surface 4.5
      brand surface-raised 4.5
      on-brand brand 4.5
      on-brand brand-hover 4.5
      brand-ink brand-soft 4.5
      on-accent accent 4.5
      accent-ink accent-soft 4.5
      on-danger danger 4.5
      danger-ink danger-soft 4.5
      success-ink success-soft 4.5
      warning-ink warning-soft 4.5
      info-ink info-soft 4.5
      ink brand-soft 4.5
      ink accent-soft 4.5
      ink-muted brand-soft 4.5
      slot-breakfast-ink slot-breakfast-soft 4.5
      slot-breakfast surface-raised 3
      slot-breakfast slot-breakfast-soft 2.5
      ink slot-breakfast-soft 4.5
      outline slot-breakfast-soft 3
      slot-lunch-ink slot-lunch-soft 4.5
      slot-lunch surface-raised 3
      slot-lunch slot-lunch-soft 2.5
      ink slot-lunch-soft 4.5
      outline slot-lunch-soft 3
      slot-dinner-ink slot-dinner-soft 4.5
      slot-dinner surface-raised 3
      slot-dinner slot-dinner-soft 2.5
      ink slot-dinner-soft 4.5
      outline slot-dinner-soft 3
      slot-snack-ink slot-snack-soft 4.5
      slot-snack surface-raised 3
      slot-snack slot-snack-soft 2.5
      ink slot-snack-soft 4.5
      outline slot-snack-soft 3
      """;

  @Test
  void everyAppendixCPairMeetsItsMinimumInBothThemes() {
    assertThat(failures(stylesheet())).isEmpty();
  }

  @Test
  void theSystemDarkBlockMatchesMidnight() {
    String css = stylesheet();

    assertThat(tokens(css, SYSTEM_DARK)).isEqualTo(tokens(css, MIDNIGHT));
  }

  @Test
  void bothThemesDefineTheSameTokens() {
    String css = stylesheet();

    assertThat(tokens(css, MOONLIGHT).keySet()).isEqualTo(tokens(css, MIDNIGHT).keySet());
  }

  @Test
  void checkerFailsWhenALightTokenDropsBelowItsMinimum() {
    String bad = stylesheet().replace("--mm-ink: #24174d;", "--mm-ink: #c9c4d8;");

    assertThat(failures(bad)).anySatisfy(message -> assertThat(message).contains("ink on surface"));
  }

  @Test
  void checkerFailsWhenADarkTokenDropsBelowItsMinimum() {
    String bad = stylesheet().replace("--mm-ink: #f4efff;", "--mm-ink: #2a2440;");

    assertThat(failures(bad)).anySatisfy(message -> assertThat(message).contains("Midnight"));
  }

  @Test
  void checkerFailsWhenAPairUsesAMissingToken() {
    String bad = stylesheet().replace("--mm-accent-ink: #6b4300;", "");

    assertThat(failures(bad)).anySatisfy(message -> assertThat(message).contains("accent-ink"));
  }

  @Test
  void contrastIsTheWcagRatioForBlackOnWhite() {
    assertThat(contrast("000000", "ffffff")).isCloseTo(21.0, within(0.001));
    assertThat(contrast("ffffff", "ffffff")).isCloseTo(1.0, within(0.001));
  }

  // ---- the checker ----

  /** Every pair that fails in either theme, as a readable message (empty when all pass). */
  static List<String> failures(String css) {
    List<String> failures = new ArrayList<>();
    Map<String, Map<String, String>> themes = new LinkedHashMap<>();
    themes.put("Moonlight", tokens(css, MOONLIGHT));
    themes.put("Midnight", tokens(css, MIDNIGHT));
    for (String line : PAIRS.strip().split("\\R")) {
      String[] parts = line.strip().split("\\s+");
      String foreground = parts[0];
      String background = parts[1];
      double minimum = Double.parseDouble(parts[2]);
      for (Map.Entry<String, Map<String, String>> theme : themes.entrySet()) {
        Map<String, String> tokens = theme.getValue();
        boolean complete = true;
        for (String name : List.of(foreground, background)) {
          if (!tokens.containsKey(name)) {
            failures.add(theme.getKey() + ": token " + name + " is not defined in app.css");
            complete = false;
          }
        }
        if (!complete) {
          continue;
        }
        double ratio = contrast(tokens.get(foreground), tokens.get(background));
        if (ratio < minimum) {
          failures.add(
              "%s: %s on %s is %.2f:1, needs %.1f:1"
                  .formatted(theme.getKey(), foreground, background, ratio, minimum));
        }
      }
    }
    return failures;
  }

  /** The tokens of one theme block, name to six-digit hex, in the order they are declared. */
  static Map<String, String> tokens(String css, Pattern block) {
    Matcher blockMatcher = block.matcher(css);
    if (!blockMatcher.find()) {
      throw new IllegalStateException("No theme block matches " + block.pattern());
    }
    Map<String, String> tokens = new LinkedHashMap<>();
    Matcher token = TOKEN.matcher(blockMatcher.group(1));
    while (token.find()) {
      tokens.put(token.group(1), token.group(2).toLowerCase());
    }
    return tokens;
  }

  /** The WCAG 2 contrast ratio of two six-digit hex colours, from 1 to 21. */
  static double contrast(String first, String second) {
    double a = relativeLuminance(first);
    double b = relativeLuminance(second);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }

  private static double relativeLuminance(String hex) {
    double[] linear = new double[3];
    for (int channel = 0; channel < 3; channel++) {
      double value = Integer.parseInt(hex.substring(channel * 2, channel * 2 + 2), 16) / 255.0;
      linear[channel] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
  }

  private static String stylesheet() {
    try {
      return Files.readString(STYLESHEET, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
