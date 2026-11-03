package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The install hint (#126): an Install button when the browser fires {@code beforeinstallprompt},
 * manual instructions on iPhone Safari, nothing when already installed, and a 30-day dismissal.
 */
class InstallHintE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");
  private static final String HINT = "[data-install-hint]";
  private static final String IPHONE_SAFARI =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
          + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
  private static final String FIRE_PROMPT =
      """
      () => {
        window.__prompted = 0;
        const event = new Event('beforeinstallprompt');
        event.prompt = () => { window.__prompted++; return Promise.resolve(); };
        window.dispatchEvent(event);
      }
      """;

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
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
  void installButtonCallsTheBrowsersPromptAndDismissSticks(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = signIn(context);
      page.evaluate(FIRE_PROMPT);
      page.locator(HINT + " button:has-text('Telepítés'):visible").click();

      assertThat(page.evaluate("window.__prompted")).isEqualTo(1);
      assertThat(page.locator(HINT).isVisible()).isFalse();

      page.reload();
      page.evaluate(FIRE_PROMPT);
      page.locator(HINT + " button:has-text('Most nem'):visible").click();
      page.reload();
      page.evaluate(FIRE_PROMPT);

      assertThat(page.locator(HINT).isVisible()).isFalse();
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void iPhoneSafariShowsTheManualInstructions(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setUserAgent(IPHONE_SAFARI))) {
      Page page = signIn(context);

      assertThat(page.locator(HINT + " p:has-text('Főképernyőhöz adás'):visible").count())
          .isEqualTo(1);
      assertThat(page.locator(HINT + " button:has-text('Telepítés'):visible").count()).isZero();
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void anInstalledAppShowsNoHint(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setUserAgent(IPHONE_SAFARI))) {
      context.addInitScript(
          "const real = window.matchMedia.bind(window);"
              + "window.matchMedia = (q) => q.includes('standalone')"
              + " ? { matches: true, media: q, addEventListener() {}, removeEventListener() {} }"
              + " : real(q);");
      Page page = signIn(context);
      page.evaluate(FIRE_PROMPT);

      assertThat(page.locator(HINT).isVisible()).isFalse();
    }
  }

  private static Page signIn(BrowserContext context) {
    Page page = context.newPage();
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().matches("/(plan|households)"));
    return page;
  }
}
