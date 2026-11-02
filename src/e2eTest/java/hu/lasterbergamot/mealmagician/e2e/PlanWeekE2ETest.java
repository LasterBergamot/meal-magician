package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The week page (issue #97) in a phone-sized and a desktop-sized window: the Plan tab leads to
 * {@code /plan}, the phone shows the week strip and day list while the desktop shows the table,
 * today is marked in both, the stepper swaps the week and moves the address bar, Back shows the
 * previous week as a whole page, and on a desktop the arrow keys step the week.
 *
 * <p>The household is new, so every slot is empty; the planned meals are covered by {@code
 * PlanPagesTest}. Screenshots of the page in both themes go to {@code build/e2e-screenshots}. The
 * labels are the Hungarian ones, the app's default language.
 */
class PlanWeekE2ETest {

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
  void aMemberStepsThroughTheWeeks(String device, int width, int height) {
    boolean phone = device.equals("phone");
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E terv " + device + " " + System.nanoTime());

      // The Plan tab leads to the week page and is the current one there.
      page.navigate(BASE_URL + "/households");
      Locator tab = page.locator("nav[aria-label='Fő navigáció']:visible a[href='/plan']");
      tab.click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/plan"));
      assertThat(
              page.locator(
                      "nav[aria-label='Fő navigáció']:visible a[href='/plan'][aria-current='page']")
                  .count())
          .isEqualTo(1);
      assertThat(page.locator("h1").textContent()).isEqualTo("Heti terv");
      Locator title = page.locator("#plan-week h2").first();
      assertThat(title.textContent()).matches("\\d+\\. hét");
      String firstTitle = title.textContent();

      // The phone shows the strip and the day list, the desktop the table; today is marked.
      if (phone) {
        assertThat(page.locator("#plan-strip").isVisible()).isTrue();
        assertThat(page.locator("table").isVisible()).isFalse();
        assertThat(page.locator("#plan-strip a[aria-current='date']").isVisible()).isTrue();
      } else {
        assertThat(page.locator("table").isVisible()).isTrue();
        assertThat(page.locator("th[aria-current='date']").isVisible()).isTrue();
        assertThat(page.locator("#plan-strip").isVisible()).isFalse();
      }
      assertNoSidewaysScroll(page, device);
      screenshot(page, device, "light");
      page.context().addCookies(List.of(new Cookie("mm_theme", "dark").setUrl(BASE_URL)));
      page.reload();
      screenshot(page, device, "dark");
      page.context().addCookies(List.of(new Cookie("mm_theme", "light").setUrl(BASE_URL)));
      page.reload();

      // Next week: the fragment is swapped, the address bar follows, today is not in view.
      page.locator("#plan-next").click();
      page.waitForURL(url -> URI.create(url).getQuery() != null);
      assertThat(page.url()).contains("week=");
      page.locator("#plan-today").waitFor();
      assertThat(title.textContent()).isNotEqualTo(firstTitle);
      assertThat(page.locator("[aria-current='date']:visible").count()).isZero();
      assertThat(page.evaluate("document.activeElement.id")).isEqualTo("plan-next");
      assertNoSidewaysScroll(page, device);

      // Back is a whole page again, not a fragment nested into the body.
      page.goBack();
      page.waitForURL(url -> URI.create(url).getQuery() == null);
      page.locator("#plan-week h2").first().waitFor();
      assertThat(page.locator("#plan-week h2").first().textContent()).isEqualTo(firstTitle);
      assertThat(page.locator("main").count()).isEqualTo(1);
      assertThat(page.locator("#shell").count()).isEqualTo(1);

      if (!phone) {
        // The arrow keys step the week when no field has the focus.
        page.locator("h1").click();
        page.keyboard().press("ArrowRight");
        page.waitForURL(url -> URI.create(url).getQuery() != null);
        assertThat(page.url()).contains("week=");
        page.locator("#plan-today").waitFor();
        page.keyboard().press("ArrowLeft");
        page.waitForURL(url -> URI.create(url).getQuery() != null);
        page.locator("#plan-today").waitFor(hidden());
        assertThat(page.locator("#plan-week h2").first().textContent()).isEqualTo(firstTitle);
      }
    }
  }

  private static Locator.WaitForOptions hidden() {
    return new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED);
  }

  private static void assertNoSidewaysScroll(Page page, String device) {
    assertThat(
            (Boolean)
                page.evaluate(
                    "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
        .as("no horizontal scrolling on %s", device)
        .isTrue();
  }

  private static void screenshot(Page page, String device, String theme) {
    page.screenshot(
        new Page.ScreenshotOptions()
            .setFullPage(true)
            .setPath(
                Path.of("build", "e2e-screenshots", "plan-week-" + device + "-" + theme + ".png")));
  }

  /** Signs in through the stub provider and lands on the home page. */
  private static void signIn(Page page) {
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().equals("/"));
  }

  private static void createHousehold(Page page, String name) {
    page.navigate(BASE_URL + "/households");
    page.locator("#create-name").fill(name);
    page.locator("#create-form button[type=submit]").click();
    page.locator("#households li:has-text('" + name + "')").waitFor();
  }
}
