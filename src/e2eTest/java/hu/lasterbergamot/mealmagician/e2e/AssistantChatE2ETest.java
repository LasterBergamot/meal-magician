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
 * The Wizard's chat page and the occasion menu suggestions (issue #161) in a phone-sized and a
 * desktop-sized window. A member opens the chat, sends a starter chip and a typed question with
 * Ctrl+Enter, starts a new conversation, watches the counter appear from 800 characters, then asks
 * for a menu on an occasion page, adds the idea to the library and the library meal to the menu.
 *
 * <p>The stack runs with the {@code fake-ai} profile, so the "model" is a script and nothing here
 * reaches Google. Screenshots go to {@code build/e2e-screenshots} in both themes. The labels are
 * the Hungarian ones, the app's default language.
 */
class AssistantChatE2ETest {

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
  void aMemberChatsResetsAndGetsAMenuSuggestion(String device, int width, int height) {
    LocalDate birthday = LocalDate.now(ZoneId.of("Europe/Budapest")).plusDays(30);

    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = context.newPage();
      signIn(page);
      createHousehold(page, "E2E chat " + device + " " + System.nanoTime());

      // The chat: an empty state with starter chips; a chip sends its text.
      page.navigate(BASE_URL + "/assistant");
      page.locator("#chat-empty").waitFor();
      click(page.locator("#chat-empty button:has-text('Mi van most szezonban?')"));
      page.locator("#chat-thread p:has-text('Scripted answer: Mi van most szezonban?')").waitFor();
      // The first answer swaps the empty state out of band, a moment after the bubble.
      page.locator("#chat-empty")
          .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

      // A typed question, sent with Ctrl+Enter.
      page.locator("#chat-message").fill("Mit főzzünk vasárnap?");
      page.locator("#chat-message").press("Control+Enter");
      page.locator("#chat-thread p:has-text('Scripted answer: Mit főzzünk vasárnap?')").waitFor();
      shots(page, device, "chat");

      // The counter is hidden under 800 characters and shown from 800.
      Locator counter = page.locator("#chat-form p[data-format]");
      page.locator("#chat-message").fill("a".repeat(799));
      assertThat(counter.isVisible()).isFalse();
      page.locator("#chat-message").fill("a".repeat(800));
      counter.waitFor();
      assertThat(counter.textContent()).isEqualTo("800 / 1000");
      page.locator("#chat-message").fill("");

      // New conversation empties the thread and shows the empty state again.
      click(page.locator("#chat-reset"));
      page.locator("#chat-empty").waitFor();
      assertThat(page.locator("#chat-thread p").count()).isZero();

      // The occasion: a birthday, then its menu suggestions.
      page.navigate(BASE_URL + "/occasions/new");
      page.locator("#occasion-form").waitFor();
      page.locator("#occasion-name").fill("Annas " + device);
      page.locator("#occasion-date").fill(birthday.toString());
      page.locator("#occasion-type").selectOption("BIRTHDAY");
      page.locator("#occasion-form button[type=submit]").click();
      page.waitForURL(url -> URI.create(url).getPath().matches("/occasions/\\d+"));

      // The first suggestion is an idea: it goes to the library, not to the menu.
      Locator suggest = page.locator("#occasion-suggest-menu");
      suggest.waitFor();
      click(suggest);
      page.locator("[data-idea]:has-text('Tökkrémleves')").first().waitFor();
      click(page.locator("[data-idea] button:has-text('Felvétel az ételek közé')").first());
      page.locator("text=Felvéve az ételek közé").first().waitFor();
      assertThat(page.locator("#menu").textContent()).doesNotContain("Tökkrémleves");

      // Asked again, the library meal is a row; "Add to menu" puts it on the menu.
      click(page.locator("#occasion-suggest-menu"));
      page.locator("#occasion-menu-suggestions li:has-text('Tökkrémleves')").first().waitFor();
      click(
          page.locator(
                  "#occasion-menu-suggestions li:has-text('Tökkrémleves') button:has-text('Hozzáadás a menühöz')")
              .first());
      page.locator("#menu li:has-text('Tökkrémleves')").first().waitFor();
      shots(page, device, "occasion");

      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  private static void click(Locator locator) {
    locator.evaluate("element => element.scrollIntoView({ block: 'center' })");
    locator.click();
  }

  /** A viewport screenshot in both themes; the theme is switched on the page without a reload. */
  private static void shots(Page page, String device, String name) {
    for (String theme : List.of("light", "dark")) {
      page.evaluate("theme => { document.documentElement.dataset.theme = theme; }", theme);
      page.waitForTimeout(400);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(
                  Path.of(
                      "build",
                      "e2e-screenshots",
                      "assistant-chat-" + name + "-" + device + "-" + theme + ".png")));
    }
    page.evaluate("() => { document.documentElement.dataset.theme = 'light'; }");
  }

  /** Signs in through the stub provider and lands on the plan or the households page. */
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
