package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The plan template pages (issue #111, first driven in a browser by issue #113) in a phone-sized
 * and a desktop-sized window: save a day as a template from the reuse sheet, open it from the
 * template list, rename it, and delete it behind the confirm dialog.
 *
 * <p>The household is new, so the test builds its own plan. Screenshots of the list and the detail
 * page in both themes go to {@code build/e2e-screenshots}. The labels are the Hungarian ones, the
 * app's default language.
 */
class PlanTemplatesE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

  private static final Path SCREENSHOTS = Path.of("build", "e2e-screenshots");

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
  void aMemberSavesOpensRenamesAndDeletesATemplate(String device, int width, int height)
      throws IOException {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
    String meal = "Rakott krumpli " + device;

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E sablon " + device + " " + System.nanoTime());
      page.navigate(BASE_URL + "/plan");
      group(page, today + "/LUNCH").waitFor();
      quickAdd(page, today + "/LUNCH", meal);

      // Save today as the day template "Napom" through the reuse sheet.
      click(page.locator("a[data-reuse='" + today + "']:visible"));
      page.locator("dialog#reuse-sheet[open]").waitFor();
      sheetLoaded(page, "#save-template-form");
      page.locator("#template-name").fill("Napom");
      page.locator("#save-template-form button[type=submit]").click();
      page.locator("#save-template-form:has-text('Sablon elmentve')").waitFor();

      // The list shows it; open it.
      page.navigate(BASE_URL + "/plan/templates");
      page.locator("[data-template-row]:has-text('Napom')").waitFor();
      shots(page, device, "list");
      click(page.locator("[data-template-row]:has-text('Napom')"));
      page.waitForURL(url -> URI.create(url).getPath().matches("/plan/templates/\\d+"));
      assertThat(page.locator("h1").textContent()).isEqualTo("Napom");
      assertThat(page.locator("body").textContent()).contains(meal);

      // Rename it: the heading changes.
      sheetLoaded(page, "#template-rename-name");
      page.locator("#template-rename-name").fill("Hétköznap");
      page.locator("#template-rename-form button[type=submit]").click();
      page.locator("h1:has-text('Hétköznap')").waitFor();
      shots(page, device, "detail");

      // Delete it behind the confirm: back on the list, which is empty again.
      click(page.locator("button:has-text('Sablon törlése')"));
      page.locator("dialog#delete-template[open]").waitFor();
      page.locator("dialog#delete-template button:has-text('Igen, törlöm')").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/plan/templates"));
      page.locator("h2:has-text('Még nincs sablon')").waitFor();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /** The visible box of one day and slot type (the phone's row or the desktop's cell). */
  private static Locator group(Page page, String key) {
    return page.locator("[data-group='" + key + "']:visible");
  }

  /** Scrolls to the middle first, because the phone's tab bar covers the bottom edge. */
  private static void click(Locator locator) {
    locator.evaluate("element => element.scrollIntoView({ block: 'center' })");
    locator.click();
  }

  /**
   * Waits until htmx has loaded content and finished settling it. htmx attaches the listeners of
   * new content a few milliseconds after the swap, so typing straight away would be lost; a person
   * is slower than that, a test is not.
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
    group(page, key).locator("[data-slot-id]:has-text('" + name + "')").waitFor();
  }

  /** A full-page screenshot in both themes; the theme is switched on the page without a reload. */
  private static void shots(Page page, String device, String name) throws IOException {
    Files.createDirectories(SCREENSHOTS);
    for (String theme : List.of("light", "dark")) {
      page.evaluate("theme => { document.documentElement.dataset.theme = theme; }", theme);
      // The buttons fade their background, so wait for the colours to settle before the picture.
      page.waitForTimeout(400);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(
                  SCREENSHOTS.resolve(
                      "plan-template-" + name + "-" + device + "-" + theme + ".png"))
              .setFullPage(true));
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
