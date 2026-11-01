package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Signs in through the stub identity provider and lands on the hello page, in a phone-sized and a
 * desktop-sized browser window. It runs against the real container image from {@code
 * compose.e2e.yml}, so it also proves that the image starts, migrates the database and serves the
 * compiled templates and stylesheet.
 */
class SignInE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    // The app discovers the identity provider as http://oidc:18081 inside the Compose network;
    // the browser has to reach it under that very name (see compose.e2e.yml).
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
  void signingInShowsTheHelloPage(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();

      page.navigate(BASE_URL + "/");
      assertThat(page.url()).endsWith("/login");

      page.locator("a[href='/oauth2/authorization/google']").click();
      // Spring Security sends the browser back to "/?continue", so compare the path only.
      page.waitForURL(url -> URI.create(url).getPath().equals("/"));

      // The app greets in Hungarian, its default language, whatever the browser asks for.
      assertThat(page.getByRole(AriaRole.HEADING).first().textContent())
          .isEqualTo("Szia, Meal Magician!");
      // A page wider than the window would force sideways scrolling on a phone.
      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /**
   * The first household flow (#41): create a household, then rename it, using the real forms. Both
   * runs share one database, so the names carry the device and never collide. The page is opened by
   * URL because on a phone the header's link sits behind the menu button.
   */
  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void creatingAndRenamingAHouseholdWorks(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      page.navigate(BASE_URL + "/");
      page.locator("a[href='/oauth2/authorization/google']").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/"));

      page.navigate(BASE_URL + "/households");
      page.locator("#create-name").fill("E2E " + device + " alap");
      page.locator("#create-form button[type=submit]").click();
      // A successful post makes htmx load /households again, now listing the new household.
      page.locator("#households li:has-text('E2E " + device + " alap')").waitFor();

      // The new household is the current one and the person owns it, so the rename form is there.
      page.locator("#rename-name").fill("E2E " + device + " új");
      page.locator("#rename-form button[type=submit]").click();
      page.locator("#households li:has-text('E2E " + device + " új')").waitFor();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }
}
