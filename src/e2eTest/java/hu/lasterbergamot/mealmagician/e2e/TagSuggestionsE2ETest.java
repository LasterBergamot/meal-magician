package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The Tags field's suggestions on the meal form (issue #174) in a phone-sized (390 px) and a
 * desktop-sized window: one meal is saved with two tags, then on a second meal typing "vegan" (no
 * accent) offers the household's "vegán", a tap writes its exact spelling and ", " into the field,
 * the keyboard (ArrowDown, Enter) picks another, a brand new tag is simply typed, and the saved
 * meal carries all three. Screenshots of the open list in both themes go to {@code
 * build/e2e-screenshots}. The labels are the Hungarian ones, the app's default language.
 */
class TagSuggestionsE2ETest {

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
  void aMemberPicksExistingTagsAndStillTypesANewOne(String device, int width, int height)
      throws IOException {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E címkék " + device + " " + System.nanoTime());

      // The first meal creates the household's tags by plain typing, as before.
      page.navigate(BASE_URL + "/meals/new");
      page.locator("#meal-form").waitFor();
      page.locator("#meal-name").fill("Első étel " + device);
      page.locator("#meal-tags").fill("vegán, gyors");
      page.locator("#meal-tags").press("Escape");
      page.locator("#meal-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/meals"));

      page.navigate(BASE_URL + "/meals/new");
      page.locator("#meal-form").waitFor();
      String name = "Második étel " + device;
      page.locator("#meal-name").fill(name);

      Locator input = page.locator("#meal-tags");
      Locator list = page.locator("#meal-tags-listbox");
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("false");

      // Typing without the accent offers the accented tag; the list fits the screen, rows are 44
      // px.
      input.click();
      input.pressSequentially("vegan");
      Locator vegan = list.locator("[role=option][data-value='vegán']");
      vegan.waitFor();
      // The list from focusing (every tag) is replaced by htmx after the typing; wait for it.
      list.locator("[role=option][data-value='gyors']")
          .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("true");
      assertThat(list.locator("[role=option]").count()).isEqualTo(1);
      BoundingBox box = list.boundingBox();
      assertThat(box.x).isGreaterThanOrEqualTo(0);
      assertThat(box.x + box.width).isLessThanOrEqualTo(width);
      assertThat(vegan.boundingBox().height).isGreaterThanOrEqualTo(44);
      shots(page, device);

      // A tap writes the exact spelling and ", " into the field and closes the list.
      vegan.click();
      assertThat(input.inputValue()).isEqualTo("vegán, ");
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("false");

      // The keyboard: the next segment, ArrowDown to the row, Enter picks it (the form is not
      // sent).
      input.pressSequentially("gy");
      list.locator("[role=option][data-value='gyors']").waitFor();
      // The old list (with "vegán") is replaced by htmx after the typing; wait for the new one.
      vegan.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
      input.press("ArrowDown");
      assertThat(input.getAttribute("aria-activedescendant")).isNotEmpty();
      input.press("Enter");
      assertThat(input.inputValue()).isEqualTo("vegán, gyors, ");
      assertThat(page.url()).contains("/meals/new");

      // A tag that is in the field already is not offered again; a new name is simply typed.
      input.pressSequentially("zöld");
      input.press("Escape");
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("false");
      assertThat(input.inputValue()).isEqualTo("vegán, gyors, zöld");

      page.locator("#meal-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/meals"));

      // The saved meal carries all three tags and no duplicate of the existing ones was made.
      page.locator("#meal-results a:has-text('" + name + "')").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/meals/\\d+"));
      page.locator("a:has-text('Szerkesztés')").click();
      page.locator("#meal-form").waitFor();
      String tags = page.locator("#meal-tags").inputValue();
      assertThat(tags).contains("vegán").contains("gyors").contains("zöld");
      assertThat(tags.split(",")).hasSize(3);

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /** A screenshot of the page in both themes; the theme is switched without a reload. */
  private static void shots(Page page, String device) throws IOException {
    Files.createDirectories(SCREENSHOTS);
    for (String theme : List.of("light", "dark")) {
      page.evaluate("theme => { document.documentElement.dataset.theme = theme; }", theme);
      page.waitForTimeout(400);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(
                  SCREENSHOTS.resolve("tag-suggestions-open-" + device + "-" + theme + ".png")));
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
