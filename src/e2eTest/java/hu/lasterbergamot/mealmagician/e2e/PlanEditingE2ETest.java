package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.net.URI;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Editing the week plan (issue #98) in a phone-sized and a desktop-sized window: quick add with its
 * 422 first, adding a library meal, a note, reordering, moving, removing behind the confirm dialog,
 * the "someone else changed the plan" banner when two tabs edit the same slot, and the keyboard
 * path (Enter opens the pick sheet, Escape returns focus, the confirm opens from the slot sheet and
 * Escape closes only it).
 *
 * <p>The household is new, so the test builds its own plan around today. Screenshots of the pick
 * sheet, the editor, the confirm and the conflict banner in both themes go to {@code
 * build/e2e-screenshots}. The labels are the Hungarian ones, the app's default language.
 */
class PlanEditingE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    // Same mapping as the other browser tests: the browser must reach the stub provider as "oidc".
    browser =
        playwright
            .chromium()
            .launch(
                new BrowserType.LaunchOptions()
                    .setArgs(List.of("--host-resolver-rules=MAP oidc 127.0.0.1")));
  }

  @AfterAll
  static void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void aMemberPlansAndEditsTheWeek(String device, int width, int height) {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
    LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate other = today.equals(monday) ? today.plusDays(1) : monday;
    String lunch = today + "/LUNCH";
    String dinner = today + "/DINNER";
    String breakfast = today + "/BREAKFAST";
    String gulyas = "Gulyás " + device;
    String pogacsa = "Pogácsa " + device;

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E terv szerk " + device + " " + System.nanoTime());
      page.navigate(BASE_URL + "/plan");
      group(page, lunch).waitFor();
      shots(page, device, "week-empty");

      // Quick add, rejected first: the sheet opens, the empty name is refused under the field.
      openAdd(page, lunch);
      page.locator("#pick-quick-form").waitFor();
      page.locator("#pick-quick-form button[type=submit]").click();
      page.locator("#pick-quick-form:has-text('Adj nevet az ételnek.')").waitFor();
      shots(page, device, "pick-sheet");

      // Then saved: the sheet's meal is created and planned in one step.
      page.locator("#pick-quick-name").fill(gulyas);
      page.locator("#pick-quick-form button[type=submit]").click();
      item(page, lunch, gulyas).waitFor();
      assertThat(page.locator("dialog[open]").count()).isZero();

      // Pick from the library: the search narrows the list, a click plans the meal.
      openAdd(page, dinner);
      sheetLoaded(page, "#pick-q");
      page.waitForResponse(
          response -> response.url().contains("/plan/pick") && response.url().contains("q="),
          () -> page.locator("#pick-q").fill("gulyás"));
      page.locator("#pick-results button:has-text('" + gulyas + "')").click();
      item(page, dinner, gulyas).waitFor();

      // A note.
      openItem(page, lunch, gulyas);
      page.locator("#slot-note").fill("Dupla adag");
      shots(page, device, "editor");
      page.locator("#note-form button[type=submit]").click();
      item(page, lunch, "Dupla adag").waitFor();

      // Reorder: a second lunch goes after the first, then moves above it.
      quickAdd(page, lunch, pogacsa);
      openItem(page, lunch, pogacsa);
      page.locator("#reorder-up-form button").click();
      group(page, lunch)
          .locator("[data-slot-id]:first-child:has-text('" + pogacsa + "')")
          .waitFor();

      // Move to another day and slot type of the week.
      openItem(page, lunch, pogacsa);
      page.locator("#slot-move-date").selectOption(other.toString());
      page.locator("#slot-move-slot").selectOption("SNACK");
      page.locator("#move-form button[type=submit]").click();
      item(page, other + "/SNACK", pogacsa).waitFor();
      assertThat(group(page, lunch).locator("[data-slot-id]:has-text('" + pogacsa + "')").count())
          .isZero();

      // Remove, after the confirm that opens over the slot sheet.
      openItem(page, dinner, gulyas);
      Locator remove = page.locator("#slot-sheet button:has-text('Kivétel a tervből')");
      remove.click();
      page.locator("dialog#remove-slot[open]").waitFor();
      shots(page, device, "remove-confirm");
      page.locator("#remove-slot button:has-text('Igen, kiveszem')").click();
      group(page, dinner).locator("[data-slot-id]").waitFor(detached());

      // Two tabs edit the same slot: the second save is refused with a banner, nothing is written.
      openItem(page, lunch, gulyas);
      Page other2 = context.newPage();
      other2.navigate(BASE_URL + "/plan");
      openItem(other2, lunch, gulyas);
      other2.locator("#slot-note").fill("Másik tab");
      other2.locator("#note-form button[type=submit]").click();
      item(other2, lunch, "Másik tab").waitFor();
      other2.close();
      page.locator("#slot-note").fill("Késő változtatás");
      page.locator("#note-form button[type=submit]").click();
      Locator banner = page.locator("#plan-conflict [role=alert]");
      banner.waitFor();
      assertThat(banner.textContent()).contains("Valaki más közben");
      assertThat(page.locator("dialog[open]").count()).isZero();
      page.evaluate("window.scrollTo(0, 0)");
      shots(page, device, "conflict");
      page.locator("#plan-conflict a:has-text('Újratöltés')").click();
      item(page, lunch, "Másik tab").waitFor();
      assertThat(item(page, lunch, gulyas).textContent()).doesNotContain("Késő változtatás");

      // Keyboard: Enter on the add control opens the pick sheet, Escape gives the focus back.
      page.locator("a[data-add='" + breakfast + "']:visible").focus();
      page.keyboard().press("Enter");
      page.locator("dialog#pick-sheet[open]").waitFor();
      page.locator("#pick-quick-form").waitFor();
      page.keyboard().press("Escape");
      page.locator("dialog#pick-sheet[open]").waitFor(detached());
      assertThat(page.evaluate("document.activeElement.dataset.add")).isEqualTo(breakfast);

      // The confirm opens from the slot sheet; Escape closes only it and returns to its button.
      item(page, lunch, gulyas).locator("a").focus();
      page.keyboard().press("Enter");
      page.locator("dialog#slot-sheet[open]").waitFor();
      page.locator("#note-form").waitFor();
      remove = page.locator("#slot-sheet button:has-text('Kivétel a tervből')");
      remove.focus();
      page.keyboard().press("Enter");
      page.locator("dialog#remove-slot[open]").waitFor();
      page.keyboard().press("Escape");
      page.locator("dialog#remove-slot[open]").waitFor(detached());
      assertThat(page.locator("dialog#slot-sheet[open]").count()).isEqualTo(1);
      assertThat((Boolean) remove.evaluate("element => element === document.activeElement"))
          .isTrue();
      page.keyboard().press("Escape");
      page.locator("dialog#slot-sheet[open]").waitFor(detached());

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
      shots(page, device, "week-planned");
    }
  }

  private static Locator.WaitForOptions detached() {
    return new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED);
  }

  /** The visible box of one day and slot type (the phone's row or the desktop's cell). */
  private static Locator group(Page page, String key) {
    return page.locator("[data-group='" + key + "']:visible");
  }

  private static Locator item(Page page, String key, String text) {
    return group(page, key).locator("[data-slot-id]:has-text('" + text + "')");
  }

  /** Scrolls to the middle first, because the phone's tab bar covers the bottom edge. */
  private static void click(Locator locator) {
    locator.evaluate("element => element.scrollIntoView({ block: 'center' })");
    locator.click();
  }

  private static void openAdd(Page page, String key) {
    click(page.locator("a[data-add='" + key + "']:visible"));
    sheetLoaded(page, "#pick-quick-form");
  }

  private static void openItem(Page page, String key, String text) {
    click(item(page, key, text).locator("a"));
    page.locator("dialog#slot-sheet[open]").waitFor();
    sheetLoaded(page, "#note-form");
  }

  /**
   * Waits until htmx has loaded a sheet's content and finished settling it. htmx attaches the
   * listeners of new content a few milliseconds after the swap, so typing straight away would be
   * lost; a person is slower than that, a test is not.
   */
  private static void sheetLoaded(Page page, String selector) {
    page.locator(selector).waitFor();
    page.waitForFunction(
        "() => document.querySelector('.htmx-added, .htmx-settling, .htmx-swapping') === null");
  }

  private static void quickAdd(Page page, String key, String name) {
    openAdd(page, key);
    page.locator("#pick-quick-name").fill(name);
    page.locator("#pick-quick-form button[type=submit]").click();
    item(page, key, name).waitFor();
  }

  /** A viewport screenshot in both themes; the theme is switched on the page without a reload. */
  private static void shots(Page page, String device, String name) {
    for (String theme : List.of("light", "dark")) {
      page.evaluate("theme => { document.documentElement.dataset.theme = theme; }", theme);
      // The buttons fade their background, so wait for the colours to settle before the picture.
      page.waitForTimeout(400);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(
                  Path.of(
                      "build",
                      "e2e-screenshots",
                      "plan-edit-" + name + "-" + device + "-" + theme + ".png")));
    }
    page.evaluate("() => { document.documentElement.dataset.theme = 'light'; }");
  }

  /**
   * Signs in through the stub provider and lands on the plan or, for a new person, the households
   * page.
   */
  private static void signIn(Page page) {
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().matches("/(plan|households)"));
  }

  private static void createHousehold(Page page, String name) {
    page.navigate(BASE_URL + "/households");
    page.locator("#create-name").fill(name);
    page.locator("#create-form button[type=submit]").click();
    page.locator("#households li:has-text('" + name + "')").waitFor();
  }
}
