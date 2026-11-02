package hu.lasterbergamot.mealmagician;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for the JTE templates (tests.md, PLAN.md D14, security.md):
 *
 * <ul>
 *   <li>no hard-coded visible text: every word must come from {@code ctx.t("key")};
 *   <li>every {@code t("key")} used in a template exists in both message bundles;
 *   <li>nothing the Content-Security-Policy would block: no inline {@code <script>}, no inline
 *       event handlers ({@code onclick=}), no {@code style=} attributes;
 *   <li>no old or raw palette colours (R5d): templates and {@code app.js} use only the design
 *       tokens, and {@code app.css} no longer defines the old {@code brand-*} or stone scale.
 * </ul>
 *
 * The checks read the template sources, so a violation fails here, not in a browser console. The
 * little scanner at the bottom is tested too, so the guard itself cannot silently stop guarding.
 */
class UiGuardTest {

  private static final Path TEMPLATES = Path.of("src/main/jte");
  private static final String EXPRESSION = "\u0001";
  // Attributes whose value is shown to people (screen readers, tooltips, placeholders).
  private static final Pattern VISIBLE_ATTRIBUTE =
      Pattern.compile("\\b(alt|title|aria-label|aria-description|placeholder|label)=\"([^\"]*)\"");
  private static final Pattern TAG = Pattern.compile("<[^>]*>", Pattern.DOTALL);
  private static final Pattern LETTER = Pattern.compile("\\p{L}");
  private static final Pattern TRANSLATION_KEY = Pattern.compile("\\.t\\(\"([^\"]+)\"");
  private static final Path APP_SCRIPT = Path.of("src/main/resources/static/js/app.js");
  private static final Path APP_STYLESHEET = Path.of("src/main/css/app.css");
  // The old green scale and stone greys (plan-ui-redesign.md R5d), and any raw Tailwind palette
  // colour on a colour utility: pages use the design tokens only (web-ui.md).
  private static final Pattern OLD_PALETTE =
      Pattern.compile(
          "(?<!\\w)(?:brand-\\d|stone-)"
              + "|(?<![\\w-])(?:bg|text|border|ring|outline|fill|stroke|divide|from|via|to|decoration|placeholder|caret)-"
              + "(?:white|black|(?:slate|gray|zinc|neutral|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\\d+)(?![\\w-])");
  private static final Pattern OLD_SCALE_IN_CSS = Pattern.compile("--color-(?:brand-\\d|stone-)");

  // ---- the guards over the real templates ----

  @Test
  void templatesContainNoHardCodedVisibleText() {
    List<String> violations = new ArrayList<>();
    for (Path template : templates()) {
      violations.addAll(prefixed(template, literalTextIn(read(template))));
    }

    assertThat(violations).as("use ctx.t(\"key\") instead of literal text").isEmpty();
  }

  @Test
  void templatesUseNothingTheContentSecurityPolicyBlocks() {
    List<String> violations = new ArrayList<>();
    for (Path template : templates()) {
      violations.addAll(prefixed(template, cspViolationsIn(read(template))));
    }

    assertThat(violations).isEmpty();
  }

  @Test
  void everyTranslationKeyUsedInTemplatesExistsInBothBundles() {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    List<String> missing = new ArrayList<>();
    for (Path template : templates()) {
      Matcher matcher = TRANSLATION_KEY.matcher(withoutComments(read(template)));
      while (matcher.find()) {
        String key = matcher.group(1);
        if (!hu.containsKey(key) || !en.containsKey(key)) {
          missing.add(template.getFileName() + ": " + key);
        }
      }
    }

    assertThat(missing).isEmpty();
  }

  @Test
  void messageBundlesHaveEqualKeys() {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");

    assertThat(hu.stringPropertyNames())
        .containsExactlyInAnyOrderElementsOf(en.stringPropertyNames());
  }

  @Test
  void messageBundlesHaveNoBlankTexts() {
    for (String name : List.of("messages_hu.properties", "messages_en.properties")) {
      bundle(name)
          .forEach((key, value) -> assertThat(value.toString()).as(name + " " + key).isNotBlank());
    }
  }

  @Test
  void messageBundlesUseTheSamePlaceholdersInBothLanguages() {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    Pattern placeholder = Pattern.compile("\\{\\d+}");
    for (String key : hu.stringPropertyNames()) {
      assertThat(placeholders(placeholder, en.getProperty(key)))
          .as("placeholders of " + key)
          .isEqualTo(placeholders(placeholder, hu.getProperty(key)));
    }
  }

  @Test
  void iconOnlyButtonsHaveAnAccessibleName() {
    List<String> violations = new ArrayList<>();
    for (Path template : templates()) {
      violations.addAll(prefixed(template, unnamedButtonsIn(read(template))));
    }

    assertThat(violations)
        .as("an icon-only button needs an aria-label or a visually hidden text")
        .isEmpty();
  }

  @Test
  void templatesAndAppScriptUseOnlyDesignTokenColours() {
    List<String> violations = new ArrayList<>();
    for (Path template : templates()) {
      violations.addAll(prefixed(template, oldPaletteClassesIn(withoutComments(read(template)))));
    }
    violations.addAll(prefixed(APP_SCRIPT, oldPaletteClassesIn(read(APP_SCRIPT))));

    assertThat(violations)
        .as("use the design tokens (web-ui.md), not the old or raw palette")
        .isEmpty();
  }

  @Test
  void stylesheetHasNoOldBrandScale() {
    assertThat(oldScaleInStylesheet(read(APP_STYLESHEET)))
        .as("app.css must not define the old brand-* or stone scale (R5d)")
        .isEmpty();
  }

  // ---- the guards guard: tests of the scanner ----

  @Test
  void scannerFlagsLiteralTextBetweenTags() {
    assertThat(literalTextIn("<p>Hello</p>")).hasSize(1);
  }

  @Test
  void scannerFlagsLiteralTextInVisibleAttributes() {
    assertThat(literalTextIn("<img alt=\"A dish\" src=\"/x.png\">")).hasSize(1);
  }

  @Test
  void scannerIgnoresExpressionsAndSymbols() {
    assertThat(literalTextIn("<p>${ctx.t(\"a.b\")} · 12 &copy;</p>")).isEmpty();
    assertThat(literalTextIn("<img alt=\"${ctx.t(\"a.b\")}\" src=\"/x.png\">")).isEmpty();
  }

  @Test
  void scannerIgnoresDirectivesButReadsTheTextInsideContentBlocks() {
    String template =
        """
        @import a.B
        @param String x
        <%-- A comment with words --%>
        @if(x != null)<b>${x}</b>@else<i>${x}</i>@endif
        @template.layout.base(ctx = ctx, title = ctx.t("a"), content = @`<p>Hidden words</p>`)
        """;

    assertThat(literalTextIn(template)).containsExactly("Hidden words");
  }

  @Test
  void scannerRefusesJteConstructsItDoesNotKnow() {
    assertThatThrownBy(() -> literalTextIn("@raw<p>x</p>@endraw"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void scannerFlagsAButtonWithNoTextAndNoLabel() {
    assertThat(unnamedButtonsIn("<button type=\"button\"><svg></svg></button>")).hasSize(1);
    assertThat(
            unnamedButtonsIn("<button type=\"button\" aria-label=\"Bezárás\"><svg></svg></button>"))
        .isEmpty();
    assertThat(unnamedButtonsIn("<button type=\"button\"><span>${label}</span></button>"))
        .isEmpty();
    assertThat(unnamedButtonsIn("<button type=\"submit\">Mentés</button>")).isEmpty();
  }

  @Test
  void scannerFlagsInlineScriptsHandlersAndStyles() {
    assertThat(cspViolationsIn("<script>alert(1)</script>")).isNotEmpty();
    assertThat(cspViolationsIn("<button onclick=\"go()\">${x}</button>")).isNotEmpty();
    assertThat(cspViolationsIn("<p style=\"color: red\">${x}</p>")).isNotEmpty();
    assertThat(cspViolationsIn("<a href=\"javascript:go()\">${x}</a>")).isNotEmpty();
    assertThat(cspViolationsIn("<script src=\"/js/app.js\" defer></script>")).isEmpty();
    assertThat(cspViolationsIn("<button x-on:click=\"toggle\">${x}</button>")).isEmpty();
  }

  @Test
  void paletteScannerFlagsOldAndRawColours() {
    assertThat(oldPaletteClassesIn("<p class=\"text-brand-700\">")).hasSize(1);
    assertThat(oldPaletteClassesIn("<p class=\"bg-stone-100 md:hover:bg-white\">")).hasSize(2);
    assertThat(oldPaletteClassesIn("<p class=\"border-red-500 ring-blue-300 text-black\">"))
        .hasSize(3);
  }

  @Test
  void paletteScannerAllowsDesignTokensAndNonColourWords() {
    assertThat(
            oldPaletteClassesIn(
                "<p class=\"bg-brand bg-brand-soft text-brand-ink bg-accent bg-accent-soft"
                    + " text-on-accent\">"))
        .isEmpty();
    assertThat(
            oldPaletteClassesIn(
                "<p class=\"shadow-sticker-2 outline-offset-2 border-border-strong size-11"
                    + " bg-transparent text-slot-ink bg-slot-soft\">"))
        .isEmpty();
    assertThat(
            oldPaletteClassesIn("<path stroke-width=\"2\" stroke-linecap=\"round\" fill=\"none\">"))
        .isEmpty();
    assertThat(oldPaletteClassesIn("milestone-1 cornerstone")).isEmpty();
  }

  @Test
  void stylesheetScannerFlagsTheOldScaleOnly() {
    assertThat(oldScaleInStylesheet("  --color-brand-700: #15803d;")).hasSize(1);
    assertThat(oldScaleInStylesheet("  --color-brand-soft: var(--mm-brand-soft);")).isEmpty();
  }

  // ---- implementation of the scanner ----

  /** Literal words found in text nodes and visible attributes, after removing JTE syntax. */
  static List<String> literalTextIn(String template) {
    String markup = new JteMarkup(withoutComments(template)).markup();
    List<String> found = new ArrayList<>();
    Matcher attributes = VISIBLE_ATTRIBUTE.matcher(markup);
    while (attributes.find()) {
      addIfLetters(found, attributes.group(2));
    }
    String withoutScripts = markup.replaceAll("(?s)<(script|style)\\b.*?</\\1>", "");
    for (String text : TAG.matcher(withoutScripts).replaceAll("\n").split("\n")) {
      addIfLetters(found, text.strip());
    }
    return found;
  }

  /**
   * Buttons that show no text and have no {@code aria-label}, so a screen reader would announce
   * nothing. A button counts as named when its content has a translated text (a {@code ${...}}
   * expression, which is how {@code ctx.t} appears here) or any letters.
   */
  static List<String> unnamedButtonsIn(String template) {
    String markup = new JteMarkup(withoutComments(template)).markup();
    List<String> found = new ArrayList<>();
    Matcher buttons = Pattern.compile("(?s)<button\\b([^>]*)>(.*?)</button>").matcher(markup);
    while (buttons.find()) {
      String attributes = buttons.group(1);
      String content = buttons.group(2);
      boolean labelled = attributes.contains("aria-label=");
      boolean named =
          content.contains(EXPRESSION)
              || LETTER.matcher(TAG.matcher(content).replaceAll("")).find();
      if (!labelled && !named) {
        found.add(buttons.group());
      }
    }
    return found;
  }

  /** Things the CSP blocks: inline scripts, on* handlers, style attributes, javascript: URLs. */
  static List<String> cspViolationsIn(String template) {
    String markup = new JteMarkup(withoutComments(template)).markup();
    List<String> found = new ArrayList<>();
    Matcher scripts = Pattern.compile("(?s)<script\\b([^>]*)>(.*?)</script>").matcher(markup);
    while (scripts.find()) {
      if (!scripts.group(1).contains("src=") || !scripts.group(2).isBlank()) {
        found.add("inline <script>");
      }
    }
    Matcher tags = TAG.matcher(markup);
    while (tags.find()) {
      String tag = tags.group();
      if (Pattern.compile("\\son[a-z]+\\s*=").matcher(tag).find()) {
        found.add("inline event handler in " + tag);
      }
      if (Pattern.compile("\\sstyle\\s*=").matcher(tag).find()) {
        found.add("style attribute in " + tag);
      }
      if (tag.contains("javascript:")) {
        found.add("javascript: URL in " + tag);
      }
    }
    return found;
  }

  /** Old-scale and raw palette colours on colour utilities, in the order they appear. */
  static List<String> oldPaletteClassesIn(String source) {
    return allMatches(OLD_PALETTE, source);
  }

  /** The {@code --color-brand-<digit>} and {@code --color-stone} definitions of the stylesheet. */
  static List<String> oldScaleInStylesheet(String stylesheet) {
    return allMatches(OLD_SCALE_IN_CSS, stylesheet);
  }

  private static List<String> allMatches(Pattern pattern, String source) {
    List<String> found = new ArrayList<>();
    Matcher matcher = pattern.matcher(source);
    while (matcher.find()) {
      found.add(matcher.group());
    }
    return found;
  }

  private static String withoutComments(String template) {
    return template.replaceAll("(?s)<%--.*?--%>", "");
  }

  private static void addIfLetters(List<String> found, String text) {
    String withoutExpressions = text.replace(EXPRESSION, "").replace("&copy;", "").strip();
    if (LETTER.matcher(withoutExpressions).find()) {
      found.add(withoutExpressions);
    }
  }

  private static List<String> prefixed(Path template, List<String> messages) {
    return messages.stream().map(message -> template + ": " + message).toList();
  }

  private static List<String> placeholders(Pattern placeholder, String text) {
    Matcher matcher = placeholder.matcher(text);
    List<String> found = new ArrayList<>();
    while (matcher.find()) {
      found.add(matcher.group());
    }
    return found.stream().sorted().toList();
  }

  private static List<Path> templates() {
    try (Stream<Path> files = Files.walk(TEMPLATES)) {
      List<Path> templates = files.filter(file -> file.toString().endsWith(".jte")).toList();
      assertThat(templates).as("templates found under " + TEMPLATES.toAbsolutePath()).isNotEmpty();
      return templates;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Properties bundle(String name) {
    Properties properties = new Properties();
    try (var reader =
        Files.newBufferedReader(Path.of("src/main/resources", name), StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return properties;
  }

  /**
   * Turns a template into plain HTML: JTE directives disappear, every {@code ${...}} becomes one
   * marker character, and the markup of content blocks ({@code @`...`}) is kept.
   */
  private static final class JteMarkup {
    private final String source;
    private int index;

    JteMarkup(String source) {
      this.source = source;
    }

    String markup() {
      return scan(false);
    }

    private String scan(boolean insideContentBlock) {
      StringBuilder out = new StringBuilder();
      while (index < source.length()) {
        char c = source.charAt(index);
        if (insideContentBlock && c == '`') {
          index++;
          return out.toString();
        }
        if (source.startsWith("${", index)) {
          index = endOfBraces(index + 2);
          out.append(EXPRESSION);
        } else if (c == '@') {
          directive(out);
        } else {
          out.append(c);
          index++;
        }
      }
      return out.toString();
    }

    private void directive(StringBuilder out) {
      if (source.startsWith("@param", index) || source.startsWith("@import", index)) {
        int newline = source.indexOf('\n', index);
        index = newline < 0 ? source.length() : newline;
      } else if (source.startsWith("@template.", index)) {
        index = source.indexOf('(', index) + 1;
        out.append(arguments());
      } else if (source.startsWith("@if(", index)
          || source.startsWith("@elseif(", index)
          || source.startsWith("@for(", index)) {
        index = endOfParentheses(source.indexOf('(', index) + 1);
      } else if (source.startsWith("@else", index)
          || source.startsWith("@endif", index)
          || source.startsWith("@endfor", index)) {
        index +=
            source.startsWith("@endfor", index) ? 7 : source.startsWith("@endif", index) ? 6 : 5;
      } else {
        throw new IllegalStateException(
            "Unknown JTE construct, teach UiGuardTest about it: "
                + source.substring(index, Math.min(source.length(), index + 20)));
      }
    }

    /** Reads call arguments up to the closing parenthesis; returns the markup of content blocks. */
    private String arguments() {
      StringBuilder markup = new StringBuilder();
      int depth = 1;
      while (depth > 0) {
        char c = source.charAt(index);
        if (c == '"') {
          index = endOfString(index);
        } else if (source.startsWith("@`", index)) {
          index += 2;
          markup.append(scan(true));
        } else {
          depth += c == '(' ? 1 : c == ')' ? -1 : 0;
          index++;
        }
      }
      return markup.toString();
    }

    private int endOfParentheses(int from) {
      int depth = 1;
      int i = from;
      while (depth > 0) {
        char c = source.charAt(i);
        if (c == '"') {
          i = endOfString(i);
        } else {
          depth += c == '(' ? 1 : c == ')' ? -1 : 0;
          i++;
        }
      }
      return i;
    }

    private int endOfBraces(int from) {
      int depth = 1;
      int i = from;
      while (depth > 0) {
        char c = source.charAt(i);
        if (c == '"') {
          i = endOfString(i);
        } else {
          depth += c == '{' ? 1 : c == '}' ? -1 : 0;
          i++;
        }
      }
      return i;
    }

    /** Index just after the Java string literal that starts at {@code start}. */
    private int endOfString(int start) {
      int i = start + 1;
      while (source.charAt(i) != '"') {
        i += source.charAt(i) == '\\' ? 2 : 1;
      }
      return i + 1;
    }
  }
}
