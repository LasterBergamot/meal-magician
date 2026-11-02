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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The meal library's main flow (issue #88) in a phone-sized and a desktop-sized window: quick add
 * (the floating button and sheet on a phone, the inline bar on a desktop), the full form with its
 * 422 state, search and tag filter, the detail page with the recipe link, archive behind the
 * confirm dialog, the archived list and restore.
 *
 * <p>Each run creates its own household, so the meals of other tests never show up. The labels in
 * the assertions are the Hungarian ones, the app's default language.
 */
class MealPagesE2ETest {

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
  void aMemberAddsFindsEditsArchivesAndRestoresAMeal(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E ételek " + device + " " + System.nanoTime());

      // The Meals destination is in the navigation and the page starts as an empty state.
      page.navigate(BASE_URL + "/meals");
      assertThat(page.locator("nav[aria-label='Fő navigáció']:visible a[href='/meals']").count())
          .isEqualTo(1);
      page.locator("h2:has-text('Még nincs étel')").waitFor();

      // Quick add: a rejected empty name keeps the form with its message (422), then a name saves.
      String quickName = "Gyors leves " + device;
      String formId = device.equals("phone") ? "#quick-sheet-form" : "#quick-bar-form";
      if (device.equals("phone")) {
        page.locator("button:has(span.sr-only:text('Gyors hozzáadás'))").click();
        page.locator("dialog[open]").waitFor();
      }
      page.locator(formId + " button[type=submit]").click();
      page.locator(formId + " >> text=Adj nevet az ételnek.").waitFor();
      page.locator(formId + " input[name=name]").fill(quickName);
      page.locator(formId + " button[type=submit]").click();
      page.locator("#meal-results h2:has-text('" + quickName + "')").waitFor();

      // The full form: a name is needed (422), then the meal is saved with a tag and a link.
      page.locator("a[href='/meals/new']").first().click();
      page.locator("#meal-form").waitFor();
      page.locator("#meal-form button[type=submit]").click();
      page.locator("#meal-name-error").waitFor();
      String name = "Lencsefőzelék " + device;
      page.locator("#meal-name").fill(name);
      page.locator("#meal-notes").fill("Piros lencse, tejföllel");
      page.locator("#meal-recipe").fill("https://example.com/lencse");
      page.locator("#meal-tags").fill("főzelék, vegán");
      page.locator("input[name=occasion][value=HOLIDAY]").check();
      page.locator("#meal-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/meals"));
      page.locator("#meal-results h2:has-text('" + name + "')").waitFor();

      // Search by name and filter by tag swap only the results and keep the URL in step.
      page.locator("#meal-q").fill("lencse");
      page.locator("#meal-results h2:has-text('" + quickName + "')").waitFor(hiddenState());
      assertThat(page.locator("#meal-results h2").allTextContents()).containsExactly(name);
      page.locator("#meal-q").fill("");
      page.locator("#meal-results h2:has-text('" + quickName + "')").waitFor();
      page.locator("#meal-results button[aria-pressed]:has-text('vegán')").click();
      page.locator("#meal-results h2:has-text('" + quickName + "')").waitFor(hiddenState());
      assertThat(page.url()).contains("tag=");
      page.locator("#meal-results button[aria-pressed='true']:has-text('vegán')").click();
      page.locator("#meal-results h2:has-text('" + quickName + "')").waitFor();

      // The detail page shows the recipe link with rel="noopener noreferrer".
      page.locator("#meal-results a:has-text('" + name + "')").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/meals/\\d+"));
      Locator recipe = page.locator("a[href='https://example.com/lencse']");
      assertThat(recipe.getAttribute("rel")).isEqualTo("noopener noreferrer");

      // Edit: change the name, save, and see it on the detail page.
      page.locator("a:has-text('Szerkesztés')").click();
      page.locator("#meal-name").fill(name + " ráadás");
      page.locator("#meal-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/meals/\\d+"));
      assertThat(page.locator("h1").textContent()).isEqualTo(name + " ráadás");

      // Archive behind the confirm dialog: the meal leaves the list, the archived switch shows it.
      page.locator("button:has-text('Archiválás')").click();
      page.locator("dialog[open] button:has-text('Igen, archiválom')").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/meals"));
      assertThat(page.locator("#meal-results h2:has-text('ráadás')").count()).isZero();
      page.locator("#meal-results button[aria-pressed]:has-text('Archiváltak mutatása')").click();
      page.locator("#meal-results h2:has-text('ráadás')").waitFor();

      // Restore from the detail page.
      page.locator("#meal-results a:has-text('ráadás')").click();
      page.locator("button:has-text('Visszaállítás')").click();
      // The restored meal's page reloads with the Archive button back (the URL stays the same).
      page.locator("button:has-text('Archiválás')").waitFor();

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  private static Locator.WaitForOptions hiddenState() {
    return new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN);
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
