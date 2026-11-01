package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The form components (plan-ui-redesign.md R2c) on the style guide at {@code /dev/ui}, rendered in
 * the {@code dev} profile. The guide shows every state: the field with hint, error (the 422 state,
 * with the typed value kept) and disabled; the select; the search field; the segmented control with
 * two, three and four segments. These tests read the element of each state from the HTML, so a
 * template that loses a branch fails here and not in a reviewer's browser.
 */
@WebSliceTest
@ActiveProfiles("dev")
class FormComponentsPageTest {

  private static final Path FORM_FRAGMENTS = Path.of("src/main/jte/ui/_field.jte");
  private static final Path SELECT = Path.of("src/main/jte/ui/_select.jte");
  private static final Path SEARCH = Path.of("src/main/jte/ui/_search.jte");
  private static final Path SEGMENTED = Path.of("src/main/jte/ui/_segmented.jte");

  @Autowired private MockMvc mvc;

  @Test
  void rendersTheFormsSectionInHungarianByDefault() throws Exception {
    assertThat(page()).contains("Űrlapok").contains("Hibával (422)");
  }

  @Test
  void rendersAFieldWithoutHintOrErrorAsNotInvalid() throws Exception {
    String input = elementWithId(page(), "demo-name-default");

    assertThat(input).contains("aria-invalid=\"false\"").doesNotContain("aria-describedby");
    assertThat(input).doesNotContain("border-danger").contains("border-border-strong");
  }

  @Test
  void linksAFieldsHintThroughAriaDescribedBy() throws Exception {
    String html = page();

    assertThat(elementWithId(html, "demo-email-hint"))
        .contains("aria-describedby=\"demo-email-hint-hint\"")
        .contains("autocomplete=\"email\"");
    assertThat(elementWithId(html, "demo-email-hint-hint"))
        .contains("A Google-fiókod e-mail-címe legyen.")
        .startsWith("<p");
  }

  @Test
  void rendersTheFieldIn422ErrorStateWithTheTypedValueKept() throws Exception {
    String html = page();
    String input = elementWithId(html, "demo-email-error");

    assertThat(input)
        .contains("aria-invalid=\"true\"")
        .contains("aria-describedby=\"demo-email-error-error\"")
        .contains("border-danger")
        .contains("value=\"anna@example\"")
        .containsPattern("\\srequired[\\s>]");
    String message = elementWithId(html, "demo-email-error-error");
    assertThat(message)
        .contains("Adj meg egy érvényes e-mail-címet.")
        .contains("data-icon=\"alert\"")
        .contains("text-danger");
  }

  @Test
  void rendersADisabledFieldWithItsValue() throws Exception {
    String input = elementWithId(page(), "demo-name-disabled");

    assertThat(input).containsPattern("\\sdisabled[\\s>]").contains("value=\"Anna\"");
    assertThat(input).doesNotContain("disabled=\"");
  }

  @Test
  void rendersTheSelectWithTheChosenOptionSelected() throws Exception {
    String select = elementWithId(page(), "demo-period-default");

    assertThat(select).contains("aria-invalid=\"false\"").doesNotContain("aria-describedby");
    assertThat(select)
        .contains("<option value=\"day\">")
        .doesNotContain("<option value=\"day\" selected");
    assertThat(select).containsPattern("<option value=\"week\"\\s+selected[\\s>]");
    assertThat(select).contains("<option value=\"month\">");
  }

  @Test
  void rendersTheSelectIn422ErrorStateKeepingTheChoice() throws Exception {
    String html = page();
    String select = elementWithId(html, "demo-period-error");

    assertThat(select)
        .contains("aria-invalid=\"true\"")
        .contains("aria-describedby=\"demo-period-error-error\"")
        .contains("border-danger");
    assertThat(select).containsPattern("<option value=\"day\"\\s+selected[\\s>]");
    assertThat(elementWithId(html, "demo-period-error-error"))
        .contains("Válassz időszakot a listából.");
  }

  @Test
  void rendersADisabledSelect() throws Exception {
    String select = elementWithId(page(), "demo-period-disabled");

    assertThat(select).containsPattern("\\sdisabled[\\s>]");
    assertThat(select).containsPattern("<option value=\"month\"\\s+selected[\\s>]");
  }

  @Test
  void rendersTheSearchFieldAsAPillWithAHiddenLabel() throws Exception {
    String html = page();

    assertThat(html).contains("role=\"search\"");
    assertThat(html)
        .contains("<label for=\"demo-search-value\" class=\"sr-only\">Ételek keresése</label>");
    assertThat(elementWithId(html, "demo-search-value"))
        .contains("type=\"search\"")
        .contains("value=\"Gulyás\"")
        .contains("placeholder=\"Keresés névre\"")
        .contains("rounded-full");
    assertThat(elementWithId(html, "demo-search-disabled")).containsPattern("\\sdisabled[\\s>]");
  }

  @Test
  void rendersTheSegmentedControlWithOneActiveSegmentPerGroup() throws Exception {
    String html = page();

    // Three groups (two, three and four segments), one active segment in each.
    assertThat(count(html, "role=\"tablist\"")).isEqualTo(3);
    assertThat(count(html, "aria-selected=\"true\"")).isEqualTo(3);
    assertThat(count(html, "aria-selected=\"false\"")).isEqualTo(2 + 3 + 4 - 3);
    // The check icon of each active segment. The icon grid of the style guide also draws a check,
    // so count only the icons that follow an active tab.
    assertThat(countPattern(html, "aria-selected=\"true\"[^>]*>\\s*<svg[^>]*data-icon=\"check\""))
        .isEqualTo(3);
    assertThat(html).contains("Ételek</span>").contains("Alkalmak</span>");
  }

  @Test
  void segmentedControlInTheStyleGuideIsPlainLinksThatDoNotSwapThePage() throws Exception {
    String html = page();

    assertThat(count(html, "href=\"#dev-forms\"")).isEqualTo(9);
    assertThat(html).doesNotContain("hx-get=\"#dev-forms\"");
    assertThat(html).doesNotContain("hx-push-url=");
  }

  @Test
  void formComponentsUseNoOldPaletteClass() throws IOException {
    for (Path fragment : new Path[] {FORM_FRAGMENTS, SELECT, SEARCH, SEGMENTED}) {
      String source = Files.readString(fragment, StandardCharsets.UTF_8);

      assertThat(source).as(fragment.toString()).doesNotContainPattern("brand-\\d|stone-");
    }
  }

  private String page() throws Exception {
    return mvc.perform(get("/dev/ui"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  /**
   * The element with this id, as HTML: its start tag only for a void element such as an input, or
   * the whole element up to its end tag (so the option, hint or message text inside it is there).
   */
  private static String elementWithId(String html, String id) {
    Matcher start =
        Pattern.compile("<([a-z]+)[^>]*\\sid=\"" + Pattern.quote(id) + "\"[^>]*>").matcher(html);
    if (!start.find()) {
      throw new AssertionError("No element with id " + id);
    }
    String name = start.group(1);
    if (name.equals("input")) {
      return start.group();
    }
    int close = html.indexOf("</" + name + ">", start.end());
    return html.substring(start.start(), close + name.length() + 3);
  }

  /** Number of occurrences of a plain text in the HTML. */
  private static int count(String html, String text) {
    return countPattern(html, Pattern.quote(text));
  }

  /** Number of matches of a regular expression in the HTML. */
  private static int countPattern(String html, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(html);
    int found = 0;
    while (matcher.find()) {
      found++;
    }
    return found;
  }
}
