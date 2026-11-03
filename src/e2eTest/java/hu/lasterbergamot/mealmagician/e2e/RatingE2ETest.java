package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
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
 * Rating a meal (issue #128) in a phone-sized and a desktop-sized window: the "How did it go?" card
 * on the plan page, the rating form in the slot sheet (thumb, stars, comment), changing and
 * clearing the rating, and "Not now" hiding the card for the rest of the day.
 *
 * <p>The household is new, so the test plans its own meal for yesterday through the pick page's
 * quick add (the week code comes from the date, not from "previous week"). Screenshots of the card
 * and the form in both themes go to {@code build/e2e-screenshots}. The labels are the Hungarian
 * ones, the app's default language.
 */
class RatingE2ETest {

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
  void aMemberRatesChangesAndClearsAPastMealAndSnoozesThePrompt(
      String device, int width, int height) {
    LocalDate yesterday = LocalDate.now(ZoneId.of("Europe/Budapest")).minusDays(1);
    String meal = "Gulyás " + device + " " + System.nanoTime();

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E értékelés " + device + " " + System.nanoTime());

      // Yesterday's lunch, planned through the pick page (works without the week's sheets).
      page.navigate(BASE_URL + "/plan/pick?date=" + yesterday + "&slot=LUNCH");
      page.locator("#pick-quick-name").fill(meal);
      page.locator("#pick-quick-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/plan"));

      // The card asks about it, once the plan page has loaded the prompt over htmx.
      page.navigate(BASE_URL + "/plan");
      Locator rate = page.locator("#rating-prompt-rate");
      rate.waitFor();
      assertThat(page.locator("#rating-prompt").textContent())
          .contains("Hogy sikerült?")
          .contains(meal);
      shots(page, device, "prompt");

      // Open the slot sheet from the card: thumb up, four stars, a comment, save.
      rate.click();
      page.locator("dialog#slot-sheet[open]").waitFor();
      sheetLoaded(page, "#rating-form");
      page.locator("#rating-form label:has(input[name=thumb][value=UP])").click();
      page.locator("#rating-form label:has(input[name=stars][value='4'])").click();
      page.locator("#rating-comment").fill("Nagyon finom volt");
      shots(page, device, "form");
      save(page);
      assertThat(page.locator("#rating-form").textContent()).contains("Köszönjük");
      assertThat(page.locator("#rating-form input[name=thumb][value=UP]").isChecked()).isTrue();
      assertThat(page.locator("#rating-form input[name=stars][value='4']").isChecked()).isTrue();
      shots(page, device, "form-saved");

      // The card is gone once the rating is saved (ratingSaved reloaded the prompt).
      rate.waitFor(detached());

      // Change the thumb to down.
      page.locator("#rating-form label:has(input[name=thumb][value=DOWN])").click();
      save(page);
      assertThat(page.locator("#rating-form input[name=thumb][value=DOWN]").isChecked()).isTrue();
      assertThat(page.locator("#rating-comment").inputValue()).isEqualTo("Nagyon finom volt");

      // A rejected form keeps what was typed: no thumb chosen is not possible through the UI once
      // rated, so clear first, then save with only a comment.
      Locator clear = page.locator("#rating-form button:has-text('Értékelésem törlése')");
      page.waitForResponse(response -> response.url().contains("/clear"), () -> clear.click());
      sheetLoaded(page, "#rating-form");
      assertThat(page.locator("#rating-form button:has-text('Értékelésem törlése')").count())
          .isZero();
      page.locator("#rating-comment").fill("Majdnem");
      save(page);
      assertThat(page.locator("#rating-form").textContent())
          .contains("Válassz hüvelykujjat felfelé vagy lefelé.");
      assertThat(page.locator("#rating-comment").inputValue()).isEqualTo("Majdnem");
      shots(page, device, "form-error");

      // The meal is unrated again, so the card is back; "Not now" hides it for the day.
      page.keyboard().press("Escape");
      page.locator("dialog#slot-sheet[open]").waitFor(detached());
      rate.waitFor();
      page.locator("#rating-prompt-snooze").click();
      rate.waitFor(detached());
      page.waitForResponse(
          response -> response.url().contains("/feedback/prompt") && response.status() == 200,
          page::reload);
      page.waitForFunction("() => document.querySelector('.htmx-request') === null");
      assertThat(page.locator("#rating-prompt").innerHTML().strip()).isEmpty();
      assertThat(page.locator("#rating-prompt-rate").count()).isZero();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /** Presses Save and waits until the form that answers has been swapped in and has settled. */
  private static void save(Page page) {
    page.waitForResponse(
        response ->
            response.url().contains("/feedback/slots/")
                && !response.url().contains("/clear")
                && "POST".equals(response.request().method()),
        () -> page.locator("#rating-form button:has-text('Értékelés mentése')").click());
    sheetLoaded(page, "#rating-form");
  }

  private static Locator.WaitForOptions detached() {
    return new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED);
  }

  /**
   * Waits until htmx has loaded content and finished settling it. htmx attaches the listeners of
   * new content a few milliseconds after the swap, so typing straight away would be lost; a person
   * is slower than that, a test is not.
   */
  private static void sheetLoaded(Page page, String selector) {
    page.locator(selector).waitFor();
    page.waitForFunction(
        "() => document.querySelector('.htmx-added, .htmx-settling, .htmx-swapping') === null");
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
                      "rating-" + name + "-" + device + "-" + theme + ".png")));
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
