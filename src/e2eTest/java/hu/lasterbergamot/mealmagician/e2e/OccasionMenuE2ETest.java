package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The occasion menu flow (issue #113) in a phone-sized and a desktop-sized window: add meals to an
 * occasion through the pick sheet (an empty name is refused first), move a line up, remove one
 * behind the confirm, save the menu as a template, plan the template again on a later date, and
 * find the template on the Occasions list.
 *
 * <p>The household is new. Screenshots of the pick sheet, the menu and the template page in both
 * themes go to {@code build/e2e-screenshots}. The labels are the Hungarian ones, the app's default
 * language.
 */
class OccasionMenuE2ETest {

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
  void aMemberBuildsAMenuSavesItAndPlansItAgain(String device, int width, int height)
      throws IOException {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
    String occasion = "Karácsony " + device;
    String bejgli = "Bejgli " + device;
    String halaszle = "Halászlé " + device;

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E menü " + device + " " + System.nanoTime());

      // Create the occasion.
      page.navigate(BASE_URL + "/occasions/new");
      page.locator("#occasion-form").waitFor();
      page.locator("#occasion-name").fill(occasion);
      page.locator("#occasion-date").fill(today.plusDays(30).toString());
      page.locator("#occasion-type").selectOption("HOLIDAY");
      page.locator("#occasion-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/occasions/\\d+"));
      String occasionPath = URI.create(page.url()).getPath();

      // An empty name in the pick sheet is refused under the field.
      openPickSheet(page);
      page.locator("#occasion-pick-quick-form button[type=submit]").click();
      page.locator("#occasion-pick-quick-form:has-text('Adj nevet az ételnek.')").waitFor();
      shots(page, device, "pick-sheet", false);

      // Quick-add two meals; each add reloads the page with one more line.
      page.locator("#occasion-pick-quick-name").fill(bejgli);
      page.locator("#occasion-pick-quick-form button[type=submit]").click();
      lines(page).nth(0).waitFor();
      openPickSheet(page);
      page.locator("#occasion-pick-quick-name").fill(halaszle);
      page.locator("#occasion-pick-quick-form button[type=submit]").click();
      lines(page).nth(1).waitFor();
      assertThat(lines(page).count()).isEqualTo(2);
      assertThat(lines(page).nth(0).textContent()).contains(bejgli);

      // Move the second line up: Halászlé is first.
      click(lines(page).nth(1).locator("button:has-text('Feljebb')"));
      lines(page).first().filter(new Locator.FilterOptions().setHasText(halaszle)).waitFor();
      shots(page, device, "menu", true);

      // Remove Bejgli behind the confirm, then add it again through the library list.
      Locator bejgliLine = lines(page).filter(new Locator.FilterOptions().setHasText(bejgli));
      String itemId = bejgliLine.getAttribute("data-item-id");
      click(bejgliLine.locator("button:has-text('Levétel')"));
      page.locator("dialog#remove-item-" + itemId + "[open]").waitFor();
      page.locator("dialog#remove-item-" + itemId + " button:has-text('Igen, leveszem')").click();
      lines(page).filter(new Locator.FilterOptions().setHasText(bejgli)).waitFor(detached());
      assertThat(lines(page).count()).isEqualTo(1);
      openPickSheet(page);
      click(page.locator("#occasion-pick-results button:has-text('" + bejgli + "')"));
      lines(page).nth(1).waitFor();

      // Save the menu as a template; the name is pre-filled with the occasion's name.
      sheetLoaded(page, "#occasion-template-name");
      assertThat(page.locator("#occasion-template-name").inputValue()).isEqualTo(occasion);
      click(page.locator("#occasion-save-template-form button[type=submit]"));
      page.waitForURL(url -> URI.create(url).getPath().matches("/occasions/templates/\\d+"));
      assertThat(page.locator("h1").textContent()).isEqualTo(occasion);
      shots(page, device, "template", true);

      // Plan it again a year on: the new occasion has both meals.
      sheetLoaded(page, "#occasion-use-date");
      page.locator("#occasion-use-date").fill(today.plusDays(395).toString());
      click(page.locator("#occasion-use-form button[type=submit]"));
      page.waitForURL(url ->
          URI.create(url).getPath().matches("/occasions/\\d+")
              && !URI.create(url).getPath().equals(occasionPath));
      assertThat(lines(page).count()).isEqualTo(2);
      assertThat(page.locator("#menu").textContent()).contains(bejgli).contains(halaszle);

      // The Occasions list shows the template.
      page.navigate(BASE_URL + "/occasions");
      page.locator("[data-occasion-template-id]:has-text('" + occasion + "')").waitFor();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  private static Locator lines(Page page) {
    return page.locator("[data-item-id]");
  }

  private static Locator.WaitForOptions detached() {
    return new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED);
  }

  /** Scrolls to the middle first, because the phone's tab bar covers the bottom edge. */
  private static void click(Locator locator) {
    locator.evaluate("element => element.scrollIntoView({ block: 'center' })");
    locator.click();
  }

  /** Opens the pick sheet and waits until htmx has loaded and settled its content. */
  private static void openPickSheet(Page page) {
    click(page.locator("#occasion-add-meal"));
    page.locator("dialog#occasion-pick-sheet[open]").waitFor();
    sheetLoaded(page, "#occasion-pick-quick-form");
  }

  /**
   * Waits until htmx has loaded content and finished settling it. htmx attaches the listeners of
   * new content a few milliseconds after the swap, so typing straight away would be lost; a person
   * is slower than that, a test is not.
   */
  private static void sheetLoaded(Page page, String selector) {
    page.locator(selector).first().waitFor();
    page.waitForFunction(
        "() => document.querySelector('.htmx-added, .htmx-settling, .htmx-swapping') === null");
  }

  /** A screenshot in both themes; the theme is switched on the page without a reload. */
  private static void shots(Page page, String device, String name, boolean fullPage)
      throws IOException {
    Files.createDirectories(SCREENSHOTS);
    for (String theme : List.of("light", "dark")) {
      page.evaluate("theme => { document.documentElement.dataset.theme = theme; }", theme);
      // The buttons fade their background, so wait for the colours to settle before the picture.
      page.waitForTimeout(400);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(
                  SCREENSHOTS.resolve(
                      "occasion-menu-" + name + "-" + device + "-" + theme + ".png"))
              .setFullPage(fullPage));
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
