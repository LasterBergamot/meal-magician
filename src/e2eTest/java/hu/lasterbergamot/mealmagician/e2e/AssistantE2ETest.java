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
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The wizard (issue #160) in a phone-sized and a desktop-sized window: open "Suggest my week", send
 * a request with a quick-request chip, see the draft (library meals as rows, the idea as a dashed
 * card), send a follow-up, accept it and find the meals on the plan.
 *
 * <p>The stack runs with the {@code fake-ai} profile (compose.e2e.yml), so the "model" is a script
 * and nothing here reaches Google. A new household has no meals, so the test first adds three on
 * this week's Monday, Tuesday and Wednesday lunch slots (the plan's own quick-add), which become
 * the library the script draws from. Screenshots of the request step, the draft and the accepted
 * plan in both themes go to {@code build/e2e-screenshots}. The labels are the Hungarian ones, the
 * app's default language.
 */
class AssistantE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

  /** Tailwind's {@code lg} breakpoint: the week is a table from this width, a list below it. */
  private static final int DESKTOP_FROM = 1024;

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
  void aMemberAsksForAWeekRefinesItAndAcceptsIt(String device, int width, int height) {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
    LocalDate mon =
        today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate tue = mon.plusDays(1);
    LocalDate wed = mon.plusDays(2);
    String first = "Lecsó " + device;
    String second = "Gulyás " + device;
    String third = "Rakott krumpli " + device;

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E wizard " + device + " " + System.nanoTime());
      page.navigate(BASE_URL + "/plan");
      group(page, mon + "/LUNCH").waitFor();
      quickAdd(page, mon + "/LUNCH", first);
      quickAdd(page, tue + "/LUNCH", second);
      quickAdd(page, wed + "/LUNCH", third);

      // The week has meals, so the empty-week hint is not shown. The entry loads over htmx.
      Locator open = page.locator("#plan-assistant-week");
      open.waitFor();
      assertThat(page.locator("#plan-assistant-hint").count()).isZero();

      // Step 1: the request, with one chip.
      click(open);
      page.locator("dialog#assistant-sheet[open]").waitFor();
      sheetLoaded(page, "#assistant-request-form");
      page.locator("#assistant-request").fill("Szerdán vendégek jönnek");
      page.locator("#assistant-request-form label:has-text('Kevesebb hús')").click();
      shots(page, device, "request");

      // Steps 2 and 3: the draft arrives. It shows the library meals and the idea, and writes
      // nothing to the plan.
      page.locator("#assistant-request-form button[type=submit]").click();
      page.locator("#assistant-draft").waitFor();
      assertThat(page.locator("#assistant-draft").textContent()).contains("Tökkrémleves");
      assertThat(page.locator("#assistant-days li").count()).isGreaterThanOrEqualTo(4);
      shots(page, device, "draft");

      // A follow-up replaces the draft (the script answers with a "revised" week).
      page.locator("#assistant-refine-input").fill("Mindig vacsora legyen");
      page.locator("#assistant-refine-form button[type=submit]").click();
      page.locator("#assistant-draft:has-text('Átdolgozott hét.')").waitFor();

      // Step 4: accept and fill the empty slots. The plan page reloads with the meals in it, and
      // says how it went.
      click(page.locator("#assistant-accept"));
      page.waitForURL(url -> URI.create(url).getPath().equals("/plan"));
      page.locator("#assistant-notice:has-text('Bekerült a hetedbe')").waitFor();
      // The revised draft put the three meals at dinner on Monday, Tuesday and Wednesday (in some
      // order), next to the lunches that were already there.
      for (LocalDate day : List.of(mon, tue, wed)) {
        group(page, day + "/DINNER").locator("[data-slot-id]").waitFor();
      }
      for (String meal : List.of(first, second, third)) {
        assertThat(page.locator("#plan-week").textContent()).contains(meal);
      }
      shots(page, device, "accepted");

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /**
   * The box of one day and slot type in the layout this window shows: the phone's row (an {@code
   * li}) below {@code lg}, the desktop's cell (a {@code div}) from {@code lg} up. The window's
   * width picks it, not {@code :visible}. Right after a save reloads the page, the stylesheet may
   * not apply yet, and then both layouts count as visible (a strict-mode violation).
   */
  private static Locator group(Page page, String key) {
    String tag = page.viewportSize().width < DESKTOP_FROM ? "li" : "div";
    return page.locator(tag + "[data-group='" + key + "']");
  }

  /** The add control of one day and slot type, in the layout this window shows. */
  private static Locator add(Page page, String key) {
    return group(page, key).locator("a[data-add='" + key + "']");
  }

  private static Locator item(Page page, String key, String text) {
    return group(page, key).locator("[data-slot-id]:has-text('" + text + "')");
  }

  /** Scrolls to the middle first, because the phone's tab bar covers the bottom edge. */
  private static void click(Locator locator) {
    locator.evaluate("element => element.scrollIntoView({ block: 'center' })");
    locator.click();
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
    click(add(page, key));
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
                      "assistant-" + name + "-" + device + "-" + theme + ".png")));
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
