package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The segmented control (ui/_segmented.jte) driven from the keyboard, as the WAI-ARIA tabs pattern
 * asks: the arrow keys move the focus between the segments, Home and End go to the ends, and the
 * arrow keys do not follow a link by themselves, so the page does not change until Enter is used.
 * It runs on the style guide, which is served by the dev profile the stack runs with.
 */
class SegmentedArrowKeysE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

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

  @Test
  void arrowKeysMoveFocusAcrossTheSegmentsWithoutLeavingThePage() {
    try (BrowserContext context = browser.newContext()) {
      Page page = signIn(context);
      page.navigate(BASE_URL + "/dev/ui");

      Locator tabs = page.locator("[role=tablist]").first().locator("[role=tab]");
      assertThat(tabs.count()).as("the control has several segments").isGreaterThan(1);
      int last = tabs.count() - 1;

      tabs.nth(0).focus();
      page.keyboard().press("ArrowRight");
      assertThat(isFocused(tabs.nth(1))).as("ArrowRight moves to the next segment").isTrue();

      page.keyboard().press("ArrowLeft");
      assertThat(isFocused(tabs.nth(0))).as("ArrowLeft moves back").isTrue();

      page.keyboard().press("ArrowLeft");
      assertThat(isFocused(tabs.nth(last)))
          .as("ArrowLeft from the first segment wraps to the last")
          .isTrue();

      page.keyboard().press("ArrowRight");
      assertThat(isFocused(tabs.nth(0)))
          .as("ArrowRight from the last segment wraps to the first")
          .isTrue();

      page.keyboard().press("End");
      assertThat(isFocused(tabs.nth(last))).as("End goes to the last segment").isTrue();

      page.keyboard().press("Home");
      assertThat(isFocused(tabs.nth(0))).as("Home goes to the first segment").isTrue();

      assertThat(URI.create(page.url()).getPath())
          .as("moving with the arrow keys does not follow a link")
          .isEqualTo("/dev/ui");
    }
  }

  private static Page signIn(BrowserContext context) {
    Page page = context.newPage();
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().equals("/"));
    return page;
  }

  private static boolean isFocused(Locator locator) {
    return (Boolean) locator.evaluate("element => element === document.activeElement");
  }
}
