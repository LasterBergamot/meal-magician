package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
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
 * The ingredient picker on the meal form (issue #156) in a phone-sized and a desktop-sized window:
 * open the list, type without accents ("tojas") to narrow it, pick the accented starter name, type
 * a name that is not listed and add it with one tap, add and remove one with the keyboard
 * (ArrowDown, Enter, Backspace), save, and see both ingredients on the detail page. Screenshots of
 * the open list in both themes go to {@code build/e2e-screenshots}. The labels are the Hungarian
 * ones, the app's default language.
 */
class IngredientPickerE2ETest {

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

  /**
   * Narrows the Add rows to the one for {@code text}. The list is replaced by htmx after each
   * keystroke, so an Add row for an earlier search can still be on screen when the test looks.
   */
  private static Locator.FilterOptions addRow(String text) {
    return new Locator.FilterOptions().setHasText(text);
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void aMemberPicksSearchesAndAddsIngredientsAndSavesThem(String device, int width, int height)
      throws IOException {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E hozzávalók " + device + " " + System.nanoTime());

      page.navigate(BASE_URL + "/meals/new");
      page.locator("#meal-form").waitFor();
      String name = "Tojásos étel " + device;
      page.locator("#meal-name").fill(name);

      Locator input = page.locator("#meal-ingredients");
      Locator list = page.locator("#meal-ingredients-listbox");
      Locator chips = page.locator("ul[aria-label='Kiválasztott hozzávalók'] li[data-chip]");
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("false");

      // Open: focusing the field shows the list, and it fits the screen with 44 px rows.
      input.click();
      list.locator("[role=option]").first().waitFor();
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("true");
      BoundingBox box = list.boundingBox();
      assertThat(box.x).isGreaterThanOrEqualTo(0);
      assertThat(box.x + box.width).isLessThanOrEqualTo(width);
      assertThat(list.locator("[role=option]").first().boundingBox().height)
          .isGreaterThanOrEqualTo(44);

      // Search without accents finds the accented starter name; picking it makes a chip.
      input.pressSequentially("tojas");
      Locator egg = list.locator("[role=option][data-value='tojás']");
      egg.waitFor();
      shots(page, device, "open");
      egg.click();
      assertThat(chips.count()).isEqualTo(1);
      assertThat(page.locator("input[type=hidden][name=ingredients]").first().inputValue())
          .isEqualTo("tojás");
      assertThat(input.inputValue()).isEmpty();

      // A name that is not listed is added with one tap on the "Add" row.
      input.pressSequentially("kekszmorzsa");
      Locator add = list.locator("[role=option][data-add]").filter(addRow("kekszmorzsa"));
      add.waitFor();
      assertThat(add.textContent()).contains("kekszmorzsa");
      assertThat(list.locator("[role=option]").count()).isEqualTo(1);
      add.click();
      assertThat(chips.count()).isEqualTo(2);

      // The keyboard: type a new name, ArrowDown to its row, Enter adds it; Backspace in the empty
      // field removes the last chip again; Escape closes the list.
      input.pressSequentially("zzfűszer");
      list.locator("[role=option][data-add]").filter(addRow("zzfűszer")).waitFor();
      input.press("ArrowDown");
      assertThat(input.getAttribute("aria-activedescendant")).isNotEmpty();
      input.press("Enter");
      assertThat(chips.count()).isEqualTo(3);
      input.press("Backspace");
      assertThat(chips.count()).isEqualTo(2);
      input.press("Escape");
      assertThat(input.getAttribute("aria-expanded")).isEqualTo("false");

      // A chip's remove button has a name and is at least 44 px square.
      Locator remove = chips.first().locator("button");
      assertThat(remove.getAttribute("aria-label")).isEqualTo("tojás eltávolítása");
      assertThat(remove.boundingBox().width).isGreaterThanOrEqualTo(44);
      assertThat(remove.boundingBox().height).isGreaterThanOrEqualTo(44);

      // Save: the detail page shows both ingredients.
      page.locator("#meal-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/meals"));
      page.locator("#meal-results a:has-text('" + name + "')").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/meals/\\d+"));
      assertThat(page.locator("dd").allTextContents().toString())
          .contains("tojás")
          .contains("kekszmorzsa")
          .doesNotContain("zzfűszer");

      // Edit keeps the chips; the list can be filtered by an ingredient.
      page.locator("a:has-text('Szerkesztés')").click();
      page.locator("#meal-form").waitFor();
      assertThat(chips.count()).isEqualTo(2);
      page.navigate(BASE_URL + "/meals");
      page.locator("#meal-results button[aria-pressed]:has-text('kekszmorzsa')").click();
      page.locator("#meal-results button[aria-pressed='true']:has-text('kekszmorzsa')").waitFor();
      assertThat(page.url()).contains("ingredient=");
      assertThat(page.locator("#meal-results h2").allTextContents()).containsExactly(name);

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  /** A screenshot of the page in both themes; the theme is switched without a reload. */
  private static void shots(Page page, String device, String name) throws IOException {
    Files.createDirectories(SCREENSHOTS);
    for (String theme : List.of("light", "dark")) {
      page.evaluate("theme => { document.documentElement.dataset.theme = theme; }", theme);
      page.waitForTimeout(400);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(
                  SCREENSHOTS.resolve(
                      "ingredient-picker-" + name + "-" + device + "-" + theme + ".png")));
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
