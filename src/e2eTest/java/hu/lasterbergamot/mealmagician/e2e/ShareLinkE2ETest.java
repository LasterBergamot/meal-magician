package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Media;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Printing and sharing a week (issue #131) in a phone-sized and a desktop-sized window: a member
 * plans a meal for today's lunch, makes a 7-day link in the share sheet, copies it, opens it in a
 * brand-new browser context that is signed out (a new context, not a cleared cookie jar), sees the
 * meal there, then revokes the link behind its confirm and finds the link dead. The print page is
 * opened with print media emulated and photographed.
 *
 * <p>Screenshots go to {@code build/e2e-screenshots}. The labels are the Hungarian ones, the app's
 * default language.
 */
class ShareLinkE2ETest {

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
  void aMemberSharesTheWeekAndRevokesTheLink(String device, int width, int height) {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
    String meal = "Paprikás " + device + " " + System.nanoTime();

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      context.grantPermissions(List.of("clipboard-read", "clipboard-write"));
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E megosztás " + device + " " + System.nanoTime());

      // Today's lunch, planned through the pick page's quick add.
      page.navigate(BASE_URL + "/plan/pick?date=" + today + "&slot=LUNCH");
      page.locator("#pick-quick-name").fill(meal);
      page.locator("#pick-quick-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/plan"));

      // Open the share sheet, make a 7-day link (the default) and copy it.
      page.navigate(BASE_URL + "/plan");
      page.locator("#plan-share-week").click();
      page.locator("dialog#share-sheet[open]").waitFor();
      settled(page, "#share-panel");
      assertThat(page.locator("#share-expiry").inputValue()).isEqualTo("7d");
      shot(page, device, "sheet");
      page.locator("#share-panel form button[type=submit]").click();
      page.locator("#share-link-url").waitFor();
      settled(page, "#share-link-url");
      String url = page.locator("#share-link-url").inputValue();
      assertThat(url).startsWith(BASE_URL + "/share/");
      assertThat(url.substring((BASE_URL + "/share/").length())).hasSize(43);
      page.locator("#share-panel button:has-text('Másolás')").click();
      page.locator("#share-panel p[role=status]:has-text('Kimásolva')").waitFor();
      assertThat((String) page.evaluate("navigator.clipboard.readText()")).isEqualTo(url);
      assertThat(page.locator("#share-links").textContent()).contains("Aktív");
      shot(page, device, "created");

      // A fresh, signed-out context sees the meal and receives no cookie.
      try (BrowserContext visitor =
          browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
        Page shared = visitor.newPage();
        Response response = shared.navigate(url);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers()).doesNotContainKey("set-cookie");
        assertThat(shared.locator("main").textContent()).contains(meal);
        assertThat(visitor.cookies()).isEmpty();
        shot(shared, device, "public");

        // Back in the app: revoke the link behind its confirm.
        page.locator("#share-links button:has-text('Visszavonás')").first().click();
        page.locator("dialog[role=alertdialog][open] button:has-text('Visszavonás')").click();
        page.locator("#share-links:has-text('Visszavonva')").waitFor();
        assertThat(page.locator("dialog[role=alertdialog][open]").count()).isZero();

        // The signed-out tab now meets the not-found page.
        Response gone = shared.reload();
        assertThat(gone.status()).isEqualTo(404);
        assertThat(shared.locator("main").textContent()).contains("Ez a link nem működik");
      }

      // The print page, with print media emulated: light, no shell, the meal, no Print button.
      page.navigate(BASE_URL + "/plan/print");
      page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
      assertThat(page.locator("main").textContent()).contains(meal);
      assertThat(page.locator("nav").count()).isZero();
      Locator printButton = page.locator("button:has-text('Nyomtatás')");
      assertThat(printButton.isVisible()).isFalse();
      shot(page, device, "print");
      page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));
      assertThat(printButton.isVisible()).isTrue();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /**
   * Waits until htmx has loaded content and finished settling it. htmx attaches the listeners of
   * new content a few milliseconds after the swap, so clicking straight away would be lost.
   */
  private static void settled(Page page, String selector) {
    page.locator(selector).waitFor();
    page.waitForFunction(
        "() => document.querySelector('.htmx-added, .htmx-settling, .htmx-swapping') === null");
  }

  private static void shot(Page page, String device, String name) {
    // The buttons fade their background, so wait for the colours to settle before the picture.
    page.waitForTimeout(400);
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(Path.of("build", "e2e-screenshots", "share-" + name + "-" + device + ".png")));
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
