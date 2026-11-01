package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The style guide at {@code /dev/ui} (plan-ui-redesign.md R2a), rendered in the {@code dev}
 * profile. It checks that every icon of the set and every button state is on the page, so a
 * template that loses a branch fails here and not in a reviewer's browser.
 */
@WebSliceTest
@ActiveProfiles("dev")
class DevUiPageTest {

  @Autowired private MockMvc mvc;

  @Test
  void rendersTheStyleGuideInHungarianByDefault() throws Exception {
    String html = page();

    assertThat(html).contains("<html lang=\"hu\"").contains("Stíluskönyv");
  }

  @Test
  void iconSetIsTheOneFromAppendixB() {
    assertThat(Arrays.stream(IconName.values()).map(IconName::id))
        .containsExactly(
            "calendar",
            "meals",
            "gift",
            "home",
            "plus",
            "check",
            "left",
            "right",
            "down",
            "breakfast",
            "lunch",
            "dinner",
            "snack",
            "search",
            "x",
            "alert",
            "info",
            "up",
            "star",
            "user",
            "more",
            "copy",
            "sidebar",
            "logout",
            "clock",
            "edit",
            "trash",
            "sparkle",
            "wand",
            "sparkles");
  }

  @Test
  void drawsEveryIconOfTheSet() throws Exception {
    String html = page();

    for (IconName icon : IconName.values()) {
      Matcher svg =
          Pattern.compile("data-icon=\"" + icon.id() + "\"[^>]*>(.*?)</svg>", Pattern.DOTALL)
              .matcher(html);
      assertThat(svg.find()).as("svg for %s", icon.id()).isTrue();
      assertThat(svg.group(1)).as("drawing of %s", icon.id()).contains("<");
    }
  }

  @Test
  void rendersTheFilledSparkleWithAnOutlineStroke() throws Exception {
    assertThat(page())
        .contains("fill=\"currentColor\" stroke=\"currentColor\" stroke-width=\"1.5\"");
  }

  @Test
  void rendersEveryVariantInEveryStateAndSize() throws Exception {
    String html = page();

    for (String variant : new String[] {"primary", "secondary", "ghost", "danger"}) {
      assertThat(html).as(variant).contains("<code class=\"text-sm text-ink-muted\">" + variant);
    }
    // 4 variants x (4 sizes + 4 states) = 32, plus 3 buttons of the content section (the alert's
    // Reload, the empty state's Add and Not now) and 7 of the overlay examples: the sheet's
    // trigger, its X, Save and Add (4) and the confirm dialog's trigger, Cancel and submit (3).
    // The layout has a button of its own and the content components (chips, alerts, cards) also use
    // font-extrabold, so match the class run only ui/_button.jte writes.
    assertThat(count(html, "whitespace-nowrap rounded-md font-sans text-sm font-extrabold"))
        .isEqualTo(42);
    // The inline and the floating add button, by the class only ui/_fab.jte writes.
    assertThat(count(html, "size-\\[58px]")).isEqualTo(2);
  }

  @Test
  void marksBusyButtonsAndDisablesDisabledAndBusyOnes() throws Exception {
    String html = page();

    // Only buttons are counted: the skeleton rows of the content section are aria-busy too.
    assertThat(count(html, "<button[^>]*aria-busy=\"true\"")).isEqualTo(4);
    // One disabled state and one busy state per variant. Scoped to <button> because the disabled
    // form controls of R2c are on the same page.
    assertThat(count(html, "<button[^>]*\\sdisabled[\\s>]")).isEqualTo(8);
    // A "disabled" attribute with any value still disables a button, so there must be none.
    assertThat(html).doesNotContain("disabled=\"");
  }

  @Test
  void keepsAScreenReaderLabelOnIconOnlyButtons() throws Exception {
    assertThat(page()).contains("<span class=\"sr-only\">Hozzáadás</span>");
  }

  @Test
  void pinsTheFloatingAddButtonAboveTheTabBarOnPhones() throws Exception {
    assertThat(page())
        .contains("fixed right-4 bottom-[calc(var(--mm-bottom-bar)_+_16px)] z-20 md:hidden");
  }

  @Test
  void rendersTheSheetAsANativeDialogNamedByItsHeadingAndOpenedByItsTrigger() throws Exception {
    String html = page();

    assertThat(html)
        .contains("id=\"dev-sheet\"")
        .contains("role=\"dialog\"")
        .contains("aria-labelledby=\"dev-sheet-title\"")
        .contains("<h2 id=\"dev-sheet-title\"")
        .contains("<span class=\"sr-only\">Bezárás</span>");
    assertThat(count(html, "x-data=\"sheet\"")).isEqualTo(2);
    assertThat(count(html, "x-on:click=\"show\"")).isEqualTo(2);
  }

  @Test
  void hasASwipeGrabberOnlyOnTheSheetNotTheConfirmDialog() throws Exception {
    assertThat(count(page(), "x-ref=\"grabber\"")).isEqualTo(1);
  }

  @Test
  void closesTheSheetAndTheConfirmDialogFromEveryCancelControl() throws Exception {
    // The sheet's X, the confirm dialog's Cancel and its submit button (the example only closes).
    assertThat(count(page(), "x-on:click=\"hide\"")).isEqualTo(3);
  }

  @Test
  void rendersTheConfirmDialogAsAnAlertDialogNamedByItsTitle() throws Exception {
    assertThat(page())
        .contains("id=\"dev-confirm\"")
        .contains("role=\"alertdialog\"")
        .contains("aria-labelledby=\"dev-confirm-title\"");
  }

  private String page() throws Exception {
    return mvc.perform(get("/dev/ui"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  /** Number of matches of a regular expression in the text. */
  private static int count(String text, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(text);
    int found = 0;
    while (matcher.find()) {
      found++;
    }
    return found;
  }
}
