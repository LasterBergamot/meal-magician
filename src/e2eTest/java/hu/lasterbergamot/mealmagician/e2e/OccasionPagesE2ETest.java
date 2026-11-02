package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ColorScheme;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The Occasions flow (issue #112) in a phone-sized and a desktop-sized window: the empty list, the
 * create form with its 422 message, the detail page, edit, and delete behind the confirm dialog. It
 * also saves screenshots of the list, the form and the detail page in both themes under {@code
 * build/e2e-screenshots/} for a visual check.
 *
 * <p>Each run creates its own household. The labels in the assertions are the Hungarian ones, the
 * app's default language.
 */
class OccasionPagesE2ETest {

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
  void aMemberCreatesViewsEditsAndDeletesAnOccasion(String device, int width, int height)
      throws IOException {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E alkalom " + device + " " + System.nanoTime());

      // The Occasions destination is in the navigation and the empty list invites the first one.
      page.navigate(BASE_URL + "/occasions");
      Locator tab = page.locator("nav[aria-label='Fő navigáció']:visible a[href='/occasions']");
      assertThat(tab.count()).isEqualTo(1);
      page.locator("h2:has-text('Még nincs alkalom')").waitFor();
      assertNoHorizontalScroll(page, device);

      // Create: an empty form is refused under the name, and the typed values are kept.
      page.locator("a[href='/occasions/new']").first().click();
      page.locator("#occasion-form").waitFor();
      page.locator("#occasion-form button[type=submit]").click();
      page.locator("#occasion-name-error").waitFor();
      assertThat(page.locator("#occasion-form >> text=Adj nevet az alkalomnak.").count())
          .isEqualTo(1);
      saveScreenshot(page, device, "form");

      String name = "Szülinap " + device;
      page.locator("#occasion-name").fill(name);
      page.locator("#occasion-date").fill(LocalDate.now().plusDays(10).toString());
      page.locator("#occasion-type").selectOption("PARTY");
      page.locator("#occasion-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/occasions/\\d+"));
      assertThat(page.locator("h1").textContent()).isEqualTo(name);

      // Edit: change the name and save; the detail page shows the new name.
      page.locator("a:has-text('Szerkesztés')").click();
      page.locator("#occasion-name").fill("Nagy szülinap " + device);
      page.locator("#occasion-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/occasions/\\d+"));
      assertThat(page.locator("h1").textContent()).isEqualTo("Nagy szülinap " + device);
      saveScreenshot(page, device, "detail");

      // The list shows it as the next occasion, in the gold card.
      page.navigate(BASE_URL + "/occasions");
      page.locator("[data-variant='accent'] a:has-text('Nagy szülinap " + device + "')").waitFor();
      saveScreenshot(page, device, "list");

      // Delete behind the confirm dialog: the list is empty again.
      page.locator("a:has-text('Nagy szülinap " + device + "')").first().click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/occasions/\\d+"));
      page.locator("button:has-text('Alkalom törlése')").click();
      page.locator("dialog#delete-occasion[open]").waitFor();
      page.locator("dialog[open] button:has-text('Igen, törlöm')").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/occasions"));
      page.locator("h2:has-text('Még nincs alkalom')").waitFor();

      assertNoHorizontalScroll(page, device);
    }
  }

  private static void saveScreenshot(Page page, String device, String screen) throws IOException {
    Files.createDirectories(SCREENSHOTS);
    for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorScheme.DARK)) {
      page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(scheme));
      page.screenshot(
          new ScreenshotOptions()
              .setPath(
                  SCREENSHOTS.resolve(
                      "occasion-"
                          + screen
                          + "-"
                          + device
                          + "-"
                          + scheme.name().toLowerCase()
                          + ".png"))
              .setFullPage(true));
    }
    page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.LIGHT));
  }

  private static void assertNoHorizontalScroll(Page page, String device) {
    assertThat(
            (Boolean)
                page.evaluate(
                    "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
        .as("no horizontal scrolling on %s", device)
        .isTrue();
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
