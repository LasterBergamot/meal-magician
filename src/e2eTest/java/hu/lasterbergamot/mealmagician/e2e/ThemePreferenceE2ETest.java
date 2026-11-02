package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The theme preference (UI-D8, #87): the server reads the cookie {@code mm_theme} so the first
 * paint already has the right {@code <html data-theme>}, and the theme buttons in the shell write
 * the cookie from the browser.
 *
 * <p>The "no flash" proof runs with JavaScript switched off: if {@code data-theme} is right then,
 * no script can have set it, so it was in the HTML the server sent. The sign-in page is used
 * because it needs no sign-in, and it shares {@code base.jte} with every page.
 */
class ThemePreferenceE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");
  private static final String AVATAR_BUTTON = "button[aria-haspopup='menu']:visible";
  // Midnight and Moonlight's page colours (--mm-surface in app.css).
  private static final String MIDNIGHT_SURFACE = "#130c2e";
  private static final String MOONLIGHT_SURFACE = "#f8f5ff";

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

  @ParameterizedTest(name = "cookie {0} gives data-theme {1}")
  @CsvSource({
    "light, light",
    "dark, dark",
    "system, system",
    "purple, system",
    "'', system",
  })
  void theServerPutsTheCookiesThemeInTheHtmlBeforeAnyScriptRuns(String cookie, String expected) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
      if (!cookie.isEmpty()) {
        context.addCookies(List.of(new Cookie("mm_theme", cookie).setUrl(BASE_URL)));
      }
      Page page = context.newPage();

      page.navigate(BASE_URL + "/login");

      assertThat(page.locator("html").getAttribute("data-theme")).isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void aChoiceIsAppliedAtOnceAndSurvivesAReload(String device, int width, int height) {
    // The device prefers dark, so "system" is Midnight and "light" is the override.
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setColorScheme(ColorScheme.DARK))) {
      Page page = signIn(context);
      assertThat(theme(page)).isEqualTo("system");
      assertThat(surface(page)).isEqualTo(MIDNIGHT_SURFACE);

      choose(page, device, "light");
      assertThat(theme(page)).isEqualTo("light");
      assertThat(surface(page)).as("light beats the device's dark").isEqualTo(MOONLIGHT_SURFACE);

      page.reload();
      assertThat(theme(page)).isEqualTo("light");
      assertThat(surface(page)).isEqualTo(MOONLIGHT_SURFACE);
      assertThat(pressed(page, device)).isEqualTo("light");

      choose(page, device, "dark");
      page.reload();
      assertThat(theme(page)).isEqualTo("dark");
      assertThat(surface(page)).isEqualTo(MIDNIGHT_SURFACE);
      assertThat(pressed(page, device)).isEqualTo("dark");

      choose(page, device, "system");
      page.reload();
      assertThat(theme(page)).isEqualTo("system");
      assertThat(surface(page)).as("system follows the device").isEqualTo(MIDNIGHT_SURFACE);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"light", "dark"})
  void theChoiceIsAFirstPartyCookieForAYear(String choice) {
    try (BrowserContext context = browser.newContext()) {
      Page page = signIn(context);

      choose(page, "desktop", choice);

      Cookie cookie =
          context.cookies(BASE_URL).stream()
              .filter(c -> c.name.equals("mm_theme"))
              .findFirst()
              .orElseThrow();
      assertThat(cookie.value).isEqualTo(choice);
      assertThat(cookie.path).isEqualTo("/");
      assertThat(cookie.sameSite).isEqualTo(SameSiteAttribute.LAX);
      assertThat(cookie.httpOnly).as("the script writes it, so it cannot be HttpOnly").isFalse();
      double daysLeft = (cookie.expires - System.currentTimeMillis() / 1000.0) / 86400;
      assertThat(daysLeft).isBetween(364.0, 366.0);
    }
  }

  private static String theme(Page page) {
    return page.locator("html").getAttribute("data-theme");
  }

  /** The page colour the CSS tokens give the current theme, as the hex written in app.css. */
  private static String surface(Page page) {
    return (String)
        page.evaluate(
            "getComputedStyle(document.documentElement).getPropertyValue('--mm-surface').trim()");
  }

  /** The theme whose button is marked as pressed in the visible theme control. */
  private static String pressed(Page page, String device) {
    if (device.equals("phone")) {
      page.locator(AVATAR_BUTTON).click();
    }
    String value =
        page.locator("[data-theme-choice][aria-pressed='true']:visible")
            .getAttribute("data-theme-choice");
    if (device.equals("phone")) {
      page.keyboard().press("Escape");
    }
    return value;
  }

  /** Clicks a theme button where the device shows it: the sidebar, or the avatar menu. */
  private static void choose(Page page, String device, String choice) {
    if (device.equals("phone")) {
      page.locator(AVATAR_BUTTON).click();
    }
    page.locator("[data-theme-choice='" + choice + "']:visible").click();
    page.waitForFunction("c => document.documentElement.dataset.theme === c", choice);
    if (device.equals("phone")) {
      page.keyboard().press("Escape");
    }
  }

  /** Signs in through the stub provider and lands on the home page. */
  private static Page signIn(BrowserContext context) {
    Page page = context.newPage();
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().equals("/"));
    return page;
  }
}
