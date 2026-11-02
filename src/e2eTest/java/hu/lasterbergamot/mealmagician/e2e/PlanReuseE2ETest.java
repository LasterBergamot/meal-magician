package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Reusing the plan (issue #110) in a phone-sized and a desktop-sized window: copy a day onto
 * another, save it as a template, fill an empty day from the template, and copy the whole week onto
 * the next one behind the replace confirm.
 *
 * <p>The household is new, so the test builds its own plan around today. Screenshots of the day
 * sheet and the week sheet in both themes go to {@code build/e2e-screenshots}. The labels are the
 * Hungarian ones, the app's default language.
 */
class PlanReuseE2ETest {

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
  void aMemberCopiesSavesAndAppliesDaysAndWeeks(String device, int width, int height) {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
    LocalDate mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate tue = mon.plusDays(1);
    LocalDate wed = mon.plusDays(2);
    LocalDate nextMon = mon.plusDays(7);
    String nextWeek = DateTimeFormatter.ISO_WEEK_DATE.format(nextMon).substring(0, 8);
    String meal = "Lecsó " + device;

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E reuse " + device + " " + System.nanoTime());
      page.navigate(BASE_URL + "/plan");
      group(page, mon + "/LUNCH").waitFor();
      quickAdd(page, mon + "/LUNCH", meal);

      // Copy Monday onto Tuesday.
      openReuse(page, mon);
      page.locator("#copy-form").waitFor();
      shots(page, device, "day-sheet");
      page.locator("#copy-to").fill(tue.toString());
      page.locator("#copy-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/plan"));
      item(page, tue + "/LUNCH", meal).waitFor();

      // Save Monday as a day template.
      openReuse(page, mon);
      page.locator("#template-name").fill("Napom");
      page.locator("#save-template-form button[type=submit]").click();
      page.locator("#save-template-form:has-text('Sablon elmentve')").waitFor();
      page.keyboard().press("Escape");
      page.locator("dialog#reuse-sheet[open]").waitFor(detached());

      // Fill the empty Wednesday from the template.
      openReuse(page, wed);
      assertThat(page.locator("#reuse-sheet-body").textContent())
          .contains("Erre a napra még nincs étel tervezve");
      page.locator("#reuse-sheet li[data-template-id]:has-text('Napom')").waitFor();
      page.locator("#reuse-sheet li[data-template-id] button:has-text('Hozzáadás')").click();
      item(page, wed + "/LUNCH", meal).waitFor();

      // Copy the whole week onto the next one: replace is behind a confirm.
      click(page.locator("#plan-reuse-week"));
      page.locator("dialog#reuse-sheet[open]").waitFor();
      sheetLoaded(page, "#copy-form");
      shots(page, device, "week-sheet");
      // The button's label sits in a span, so match the span; the copy form's own trigger comes
      // before the buttons that apply a template, hence first().
      click(page.locator("#reuse-sheet-body button:has(span:text-is('Csere'))").first());
      page.locator("dialog#copy-replace[open]").waitFor();
      page.locator("#copy-replace button:has-text('Igen, lecserélem')").click();
      page.waitForURL(url -> url.contains("week=" + nextWeek));
      item(page, nextMon + "/LUNCH", meal).waitFor();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  private static Locator.WaitForOptions detached() {
    return new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED);
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

  /** Opens the reuse sheet of one day and waits until htmx has settled its content. */
  private static void openReuse(Page page, LocalDate day) {
    click(page.locator("a[data-reuse='" + day + "']:visible"));
    page.locator("dialog#reuse-sheet[open]").waitFor();
    sheetLoaded(page, "#save-template-form, #reuse-sheet-body li, #reuse-sheet-body p");
  }

  /**
   * Waits until htmx has loaded a sheet's content and finished settling it. htmx attaches the
   * listeners of new content a few milliseconds after the swap, so typing straight away would be
   * lost; a person is slower than that, a test is not.
   */
  private static void sheetLoaded(Page page, String selector) {
    page.locator(selector).first().waitFor();
    page.waitForFunction(
        "() => document.querySelector('.htmx-added, .htmx-settling, .htmx-swapping') === null");
  }

  private static void quickAdd(Page page, String key, String name) {
    click(page.locator("a[data-add='" + key + "']:visible"));
    sheetLoaded(page, "#pick-quick-form");
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
                      "plan-reuse-" + name + "-" + device + "-" + theme + ".png")));
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
