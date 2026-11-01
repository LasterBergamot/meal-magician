package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The sheet and the confirm dialog on the style guide (plan-ui-redesign.md R3), driven from the
 * keyboard the way an assistive-technology user drives them: focus moves in, Tab stays inside,
 * Escape closes, and focus goes back to the button that opened the dialog. On a phone a swipe down
 * on the grabber also closes the sheet.
 *
 * <p>The style guide is served by the {@code dev} profile only, so these tests need a stack that
 * runs it. They run against the same stack as the other browser tests (see {@code
 * compose.e2e.yml}). The labels are Hungarian, the default language.
 */
class SheetAndConfirmE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    // Same mapping as SignInE2ETest: the browser must reach the stub identity provider as oidc.
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
  void sheetOpensKeepsFocusInsideAndReturnsItToTheOpener(String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = signedIntoStyleGuide(context);
      Locator opener = openerButton(page, "Lap megnyitása");

      opener.focus();
      page.keyboard().press("Enter");
      page.locator("#dev-sheet").waitFor(visible());
      assertThat(isOpen(page, "dev-sheet")).as("the sheet is open on %s", device).isTrue();
      assertThat(focusIsInside(page, "dev-sheet")).as("focus moves into the sheet").isTrue();

      // The sheet has three controls (X, Save, Add). Pressing Tab seven times goes round them
      // more than twice, and focus must stay inside every time.
      for (int press = 1; press <= 7; press++) {
        page.keyboard().press("Tab");
        assertThat(focusIsInside(page, "dev-sheet"))
            .as("Tab press %d keeps focus in the sheet", press)
            .isTrue();
      }
      page.keyboard().press("Shift+Tab");
      assertThat(focusIsInside(page, "dev-sheet")).as("Shift+Tab stays inside too").isTrue();

      page.keyboard().press("Escape");
      page.locator("#dev-sheet").waitFor(hidden());
      assertThat(isOpen(page, "dev-sheet")).isFalse();
      assertThat(isFocused(opener)).as("focus returns to the opener").isTrue();
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void confirmDialogStartsOnCancelTrapsFocusAndReturnsItOnCancel(
      String device, int width, int height) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
      Page page = signedIntoStyleGuide(context);
      Locator opener = openerButton(page, "Eltávolítás");

      opener.focus();
      page.keyboard().press("Enter");
      page.locator("#dev-confirm").waitFor(visible());
      assertThat(page.locator("#dev-confirm").getAttribute("role"))
          .as("a destructive question is an alert dialog on %s", device)
          .isEqualTo("alertdialog");
      // The first control of a confirm dialog is Cancel, the safe choice.
      assertThat(focusedText(page)).as("focus starts on Cancel").isEqualTo("Mégse");

      for (int press = 1; press <= 4; press++) {
        page.keyboard().press("Tab");
        assertThat(focusIsInside(page, "dev-confirm"))
            .as("Tab press %d keeps focus in the confirm dialog", press)
            .isTrue();
      }

      // Enter on Cancel closes the dialog without sending anything, and focus goes back.
      page.locator("#dev-confirm")
          .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Mégse"))
          .focus();
      page.keyboard().press("Enter");
      page.locator("#dev-confirm").waitFor(hidden());
      assertThat(isFocused(opener)).as("focus returns to the Remove button").isTrue();
    }
  }

  @Test
  void swipingTheGrabberDownClosesThePhoneSheet() {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(390, 844))) {
      Page page = signedIntoStyleGuide(context);
      openerButton(page, "Lap megnyitása").click();
      Locator sheet = page.locator("#dev-sheet");
      sheet.waitFor(visible());
      // The sheet rises from below the screen over duration-base. A drag that starts before it has
      // landed would miss the grabber, which is still off screen, so wait for the animation to end.
      page.waitForFunction(
          "() => document.getElementById('dev-sheet').getAnimations().length === 0");

      // The grabber is the drag handle; it is only drawn below md, so on a phone it is there.
      BoundingBox grabber = sheet.locator("[x-ref='grabber']").boundingBox();
      double x = grabber.x + grabber.width / 2;
      double y = grabber.y + grabber.height / 2;
      page.mouse().move(x, y);
      page.mouse().down();
      page.mouse().move(x, y + 160, new Mouse.MoveOptions().setSteps(12));
      page.mouse().up();

      sheet.waitFor(hidden());
      assertThat(isOpen(page, "dev-sheet")).as("a swipe down closes the sheet").isFalse();
    }
  }

  /** Signs in through the stub identity provider and opens the style guide. */
  private static Page signedIntoStyleGuide(BrowserContext context) {
    Page page = context.newPage();
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().equals("/"));
    page.navigate(BASE_URL + "/dev/ui");
    // Alpine starts with the page's load, so the components are ready once this returns.
    page.waitForLoadState();
    return page;
  }

  private static Locator openerButton(Page page, String name) {
    return page.locator("#dev-overlays")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
  }

  private static Locator.WaitForOptions visible() {
    return new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE);
  }

  private static Locator.WaitForOptions hidden() {
    return new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN);
  }

  private static boolean isOpen(Page page, String dialogId) {
    return (Boolean) page.evaluate("id => document.getElementById(id).open", dialogId);
  }

  private static boolean focusIsInside(Page page, String containerId) {
    return (Boolean)
        page.evaluate(
            "id => document.activeElement !== null && document.activeElement.closest('#' + id) !== null",
            containerId);
  }

  private static boolean isFocused(Locator locator) {
    return (Boolean) locator.evaluate("element => element === document.activeElement");
  }

  private static String focusedText(Page page) {
    return (String)
        page.evaluate(
            "() => document.activeElement ? document.activeElement.textContent.trim() : ''");
  }
}
