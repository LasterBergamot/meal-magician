package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The content and status components of the style guide at {@code /dev/ui} (plan-ui-redesign.md
 * R2b): badge, chip, slot chip, slot dot, card, row, avatar, alert, empty state and skeleton rows.
 * Each test reads one state from the rendered page, so a template that loses a colour, an icon or
 * an accessible name fails here and not in a reviewer's browser. The texts are the Hungarian ones
 * (the default language).
 *
 * <p>The last tests guard the palette: the new fragments may use only the design tokens, never a
 * raw colour such as {@code brand-700} or {@code stone-100} (the old classes are removed in R5d).
 */
@WebSliceTest
@ActiveProfiles("dev")
class ContentComponentsPageTest {

  /** Raw palette and plain colour names, which a fragment must not use. */
  private static final Pattern RAW_COLOUR =
      Pattern.compile(
          "(?<!\\w)(brand-\\d+|stone-\\d+|gray-\\d+|slate-\\d+|zinc-\\d+|green-\\d+|red-\\d+"
              + "|amber-\\d+|blue-\\d+|white|black)(?!\\w)");

  private static final List<String> NEW_FRAGMENTS =
      List.of(
          "ui/_badge.jte",
          "ui/_chip.jte",
          "ui/_slot_chip.jte",
          "ui/_slot_dot.jte",
          "ui/_card.jte",
          "ui/_row.jte",
          "ui/_avatar.jte",
          "ui/_alert.jte",
          "ui/_empty.jte",
          "ui/_skeleton_rows.jte",
          "dev/_content.jte");

  @Autowired private MockMvc mvc;

  @Test
  void badgesCarryTheColourOfTheirTone() throws Exception {
    String html = page();

    assertThat(openingTag(html, "data-tone=\"neutral\"")).contains("bg-surface-sunken text-ink");
    assertThat(openingTag(html, "data-tone=\"brand\"")).contains("bg-brand-soft text-brand-ink");
    assertThat(openingTag(html, "data-tone=\"accent\"")).contains("bg-accent text-on-accent");
    assertThat(openingTag(html, "data-tone=\"success\""))
        .contains("bg-success-soft text-success-ink");
    assertThat(openingTag(html, "data-tone=\"warning\""))
        .contains("bg-warning-soft text-warning-ink");
    assertThat(openingTag(html, "data-tone=\"danger\"")).contains("bg-danger-soft text-danger-ink");
    assertThat(openingTag(html, "data-tone=\"info\"")).contains("bg-info-soft text-info-ink");
  }

  @Test
  void currentBadgeDrawsItsCheckIcon() throws Exception {
    String current = element(page(), "data-tone=\"brand\"[^>]*>\\s*<svg[^>]*>", "</span>");

    assertThat(current).contains("data-icon=\"check\"").contains("Jelenlegi");
  }

  @Test
  void chipsReportTheirPressedStateAndSelectedOneHasACheck() throws Exception {
    String html = page();

    assertThat(count(html, "aria-pressed=\"true\"")).isEqualTo(1);
    assertThat(count(html, "aria-pressed=\"false\"")).isEqualTo(2);
    assertThat(openingTag(html, "aria-pressed=\"true\"")).contains("bg-brand text-on-brand");
    assertThat(element(html, "aria-pressed=\"true\"", "</button>")).contains("data-icon=\"check\"");
  }

  @Test
  void slotChipsShowTheirIconAndWordInTheirOwnColours() throws Exception {
    String html = page();

    assertSlotChip(html, "BREAKFAST", "slot-breakfast", "breakfast", "Reggeli");
    assertSlotChip(html, "LUNCH", "slot-lunch", "lunch", "Ebéd");
    assertSlotChip(html, "DINNER", "slot-dinner", "dinner", "Vacsora");
    assertSlotChip(html, "SNACK", "slot-snack", "snack", "Uzsonna");
  }

  @Test
  void loneSlotDotIsAnImageNamedByItsWordWithTheSlotIcon() throws Exception {
    String html = page();

    assertThat(openingTag(html, "data-slot=\"SNACK\"\\s+role=\"img\""))
        .contains("aria-label=\"Uzsonna\"");
    assertThat(element(html, "data-slot=\"SNACK\"\\s+role=\"img\"", "</span>"))
        .contains("data-icon=\"snack\"");
  }

  @Test
  void legendDotBesideAWordIsDecorative() throws Exception {
    String dot = openingTag(page(), "data-slot=\"LUNCH\"\\s+role=\"presentation\"");

    assertThat(dot).contains("aria-hidden=\"true\"").contains("size-3").contains("bg-slot");
  }

  @Test
  void cardsHaveTheColourOfTheirVariant() throws Exception {
    String html = page();

    assertThat(openingTag(html, "data-variant=\"default\"")).contains("bg-surface-raised");
    assertThat(openingTag(html, "data-variant=\"accent\"")).contains("bg-accent-soft");
    assertThat(openingTag(html, "data-variant=\"selected\"")).contains("bg-brand-soft");
  }

  @Test
  void cardLinkStretchesOverTheWholeCard() throws Exception {
    assertThat(page()).contains("after:absolute after:inset-0");
  }

  @Test
  void rowsAreListItemsAndOnlyTheCurrentOneIsSelected() throws Exception {
    String html = page();

    assertThat(count(html, "<li class=\"relative flex min-h-\\[60px\\]")).isEqualTo(3);
    assertThat(count(html, "<li class=\"[^\"]*bg-brand-soft")).isEqualTo(1);
  }

  @Test
  void avatarIsADecorativeGoldDiscWithInitials() throws Exception {
    String avatar =
        element(
            page(),
            "aria-hidden=\"true\"\\s+class=\"inline-flex shrink-0 items-center justify-center rounded-full border-2 border-outline bg-accent",
            "</span>");

    assertThat(avatar).contains("size-11").contains("text-on-accent").contains(">KA</span>");
  }

  @Test
  void successAndInfoAlertsAreStatusWhileWarningAndDangerAreAlerts() throws Exception {
    String html = page();

    assertThat(openingTag(html, "data-kind=\"success\"")).contains("role=\"status\"");
    assertThat(openingTag(html, "data-kind=\"info\"")).contains("role=\"status\"");
    assertThat(openingTag(html, "data-kind=\"warning\"")).contains("role=\"alert\"");
    assertThat(openingTag(html, "data-kind=\"danger\"")).contains("role=\"alert\"");
  }

  @Test
  void alertsDrawTheirIconAndTheWarningHasAReloadButton() throws Exception {
    String alerts = sectionAround(page(), "Közben valaki más módosította");

    assertThat(alerts)
        .contains("data-icon=\"check\"")
        .contains("data-icon=\"info\"")
        .contains("data-icon=\"alert\"")
        .contains("Újratöltés");
  }

  @Test
  void emptyStateHasTwoSparklesAndItsActions() throws Exception {
    String empty = sectionAround(page(), "Még nincs terv");

    assertThat(count(empty, "data-icon=\"sparkle\"")).isEqualTo(2);
    assertThat(empty).contains("Hozzáadás").contains("Most nem");
  }

  @Test
  void skeletonAnnouncesTheWaitAndPulsesOncePerRow() throws Exception {
    String skeleton = sectionAround(page(), "Betöltés…");

    assertThat(skeleton).contains("aria-busy=\"true\"").contains("role=\"status\"");
    assertThat(count(skeleton, "animate-pulse")).isEqualTo(3);
  }

  @Test
  void newFragmentsUseNoRawPaletteClasses() throws IOException {
    for (String fragment : NEW_FRAGMENTS) {
      assertThat(rawColoursIn(Files.readString(Path.of("src/main/jte", fragment))))
          .as(fragment)
          .isEmpty();
    }
  }

  @Test
  void paletteGuardFlagsRawColoursAndAllowsTokens() {
    assertThat(rawColoursIn("<span class=\"text-brand-700 bg-white text-on-brand\">"))
        .containsExactly("brand-700", "white");
    assertThat(rawColoursIn("<span class=\"bg-brand-soft text-slot-ink\">")).isEmpty();
  }

  private static void assertSlotChip(
      String html, String slot, String slotClass, String icon, String word) {
    String chip =
        element(html, "data-slot=\"" + slot + "\"\\s+class=\"inline-flex min-h-7", "</span>");

    assertThat(chip).contains(slotClass).contains("bg-slot-soft").contains("text-slot-ink");
    assertThat(chip).contains("data-icon=\"" + icon + "\"").contains(word);
  }

  private String page() throws Exception {
    return mvc.perform(get("/dev/ui"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  /** The opening tag that contains the given regular expression. */
  private static String openingTag(String html, String attribute) {
    Matcher matcher = Pattern.compile("<[a-z]+\\s[^>]*" + attribute + "[^>]*>").matcher(html);
    assertThat(matcher.find()).as(attribute).isTrue();
    return matcher.group();
  }

  /** The element from its opening tag (matched by the regex) up to the first closing tag. */
  private static String element(String html, String opening, String closing) {
    Matcher matcher = Pattern.compile("<[a-z]+\\s[^>]*" + opening + "[^>]*>").matcher(html);
    assertThat(matcher.find()).as(opening).isTrue();
    int end = html.indexOf(closing, matcher.end());
    assertThat(end).as(closing).isPositive();
    return html.substring(matcher.start(), end + closing.length());
  }

  /** The whole {@code <section>} that contains the marker text. */
  private static String sectionAround(String html, String marker) {
    int at = html.indexOf(marker);
    assertThat(at).as(marker).isPositive();
    return html.substring(html.lastIndexOf("<section", at), html.indexOf("</section>", at));
  }

  /** Raw palette classes found in a template: the first capture group of each match. */
  private static List<String> rawColoursIn(String template) {
    Matcher matcher = RAW_COLOUR.matcher(template);
    List<String> found = new ArrayList<>();
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    return found;
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
