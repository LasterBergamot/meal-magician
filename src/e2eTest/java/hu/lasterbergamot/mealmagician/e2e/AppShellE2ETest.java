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
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The app shell (plan-ui-redesign.md section 4) in a phone-sized and a desktop-sized window: which
 * navigation is drawn, the highlighted tab, the household switcher and its keyboard behaviour,
 * sign-out, the language choice and its use after the next sign-in, the collapsible sidebar, and
 * the tab bar that hides while a text field has focus.
 *
 * <p>Everything runs as the stub identity provider's one person, "E2E User". The language is stored
 * in the database for that person, so every test that changes it puts it back to Hungarian (the
 * other browser tests expect Hungarian). The labels in the assertions are the Hungarian ones.
 */
class AppShellE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");
  // The destinations navigation: the bottom tab bar on a phone, the sidebar on a desktop.
  private static final String NAV = "nav[aria-label='Fő navigáció']";
  private static final String SWITCHER_TRIGGER = "button[aria-haspopup='dialog']:visible";
  private static final String AVATAR_BUTTON = "button[aria-haspopup='menu']:visible";

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
  void theRightNavigationIsDrawnForTheWidth(String device, int width, int height) {
    try (BrowserContext context = newContext(width, height)) {
      Page page = signIn(context);
      boolean phone = device.equals("phone");

      assertThat(page.locator(NAV + ":visible").count())
          .as("one navigation is visible on %s", device)
          .isEqualTo(1);
      assertThat(page.locator("#sidebar").isVisible())
          .as("the sidebar shows only on a desktop")
          .isEqualTo(!phone);
      assertThat(page.locator("header:visible").count())
          .as("the top bar shows only on a phone")
          .isEqualTo(phone ? 1 : 0);
      assertThat(page.locator(SWITCHER_TRIGGER).count())
          .as("the household switcher is visible once")
          .isEqualTo(1);
      assertThat(
              (Boolean)
                  page.evaluate(
                      "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
          .as("no horizontal scrolling on %s", device)
          .isTrue();
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void theCurrentDestinationIsHighlightedAndOnlyExistingPagesAreListed(
      String device, int width, int height) {
    try (BrowserContext context = newContext(width, height)) {
      Page page = signIn(context);

      page.locator(NAV + ":visible a[href='/households']").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/households"));

      Locator current = page.locator(NAV + ":visible a[aria-current='page']");
      assertThat(current.count()).as("one tab is current on %s", device).isEqualTo(1);
      assertThat(current.getAttribute("href")).isEqualTo("/households");
      // All four destinations have pages, and the E2E profile (fake-ai) switches the assistant on,
      // so its Wizard tab is the fifth one listed.
      assertThat(page.locator(NAV + ":visible a").count())
          .as("Plan, Meals, Occasions, Household and the assistant are listed")
          .isEqualTo(5);
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void theSwitcherListsMyHouseholdsAndSwitchesTheCurrentOne(String device, int width, int height) {
    try (BrowserContext context = newContext(width, height)) {
      Page page = signIn(context);
      page.navigate(BASE_URL + "/households");
      String first = "E2E shell " + device + " első";
      String second = "E2E shell " + device + " második";
      createHousehold(page, first);
      createHousehold(page, second);

      // The household created last is the current one, so the switcher shows it.
      page.navigate(BASE_URL + "/");
      assertThat(page.locator(SWITCHER_TRIGGER).textContent()).contains(second);

      page.locator(SWITCHER_TRIGGER).click();
      Locator sheet = page.locator(sheetSelector(device));
      sheet.waitFor(visible());
      sheet.locator("form:has-text('" + first + "') button[type=submit]").click();

      page.waitForURL(url -> URI.create(url).getPath().equals("/plan"));
      assertThat(page.locator(SWITCHER_TRIGGER).textContent())
          .as("switching makes the chosen household current on %s", device)
          .contains(first);
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void theSwitcherOpensFromTheKeyboardAndEscapeReturnsFocusToIt(
      String device, int width, int height) {
    try (BrowserContext context = newContext(width, height)) {
      Page page = signIn(context);
      Locator trigger = page.locator(SWITCHER_TRIGGER);
      Locator sheet = page.locator(sheetSelector(device));

      trigger.focus();
      page.keyboard().press("Enter");
      sheet.waitFor(visible());
      assertThat(sheet.evaluate("dialog => dialog.open")).isEqualTo(true);
      assertThat(page.evaluate("() => document.activeElement.closest('dialog') !== null"))
          .as("focus moves into the switcher")
          .isEqualTo(true);

      page.keyboard().press("Escape");
      sheet.waitFor(hidden());
      assertThat(trigger.evaluate("element => element === document.activeElement"))
          .as("focus goes back to the switcher button")
          .isEqualTo(true);
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void signOutEndsTheSessionAndTheNextPageAsksForSignIn(String device, int width, int height) {
    try (BrowserContext context = newContext(width, height)) {
      Page page = signIn(context);
      if (device.equals("phone")) {
        page.locator(AVATAR_BUTTON).click();
      }

      page.locator("form[action='/logout'] button:visible").click();
      page.waitForURL(url -> URI.create(url).getPath().equals("/login"));

      page.navigate(BASE_URL + "/households");
      assertThat(URI.create(page.url()).getPath())
          .as("a signed-out visitor is sent to sign-in on %s", device)
          .isEqualTo("/login");
    }
  }

  @ParameterizedTest(name = "{0} ({1}x{2})")
  @CsvSource({"phone, 390, 844", "desktop, 1280, 800"})
  void aLanguageChosenWhileSignedInIsUsedAfterTheNextSignIn(String device, int width, int height) {
    try {
      try (BrowserContext context = newContext(width, height)) {
        switchLanguage(signIn(context), device, "en");
      }
      // A new browser has no language cookie, so the stored language has to bring English back.
      try (BrowserContext fresh = newContext(width, height)) {
        Page page = signIn(fresh);
        assertThat(page.getAttribute("html", "lang"))
            .as("the stored English is used after sign-in on %s", device)
            .isEqualTo("en");
      }
    } finally {
      restoreHungarian(width, height);
    }
  }

  @Test
  void theTopBarAvatarOnAPhoneIsNotGoldBecauseTheCurrentTabAlreadyIs() {
    try (BrowserContext context = newContext(390, 844)) {
      Page page = signIn(context);

      Locator avatar = page.locator("header:visible [data-tone]");
      assertThat(avatar.getAttribute("data-tone")).isEqualTo("soft");
      Object sameAsGold =
          page.evaluate(
              """
              () => {
                const probe = document.createElement('div');
                probe.style.background = 'var(--mm-accent)';
                document.body.append(probe);
                const gold = getComputedStyle(probe).backgroundColor;
                probe.remove();
                return getComputedStyle(document.querySelector('header [data-tone]')).backgroundColor === gold;
              }
              """);
      assertThat(sameAsGold)
          .as("the avatar disc is not filled with the gold accent")
          .isEqualTo(false);
    }
  }

  @Test
  void theCollapsedSidebarIsKeptForThisBrowser() {
    try (BrowserContext context = newContext(1280, 800)) {
      Page page = signIn(context);
      assertThat(sidebarWidth(page)).as("the sidebar starts open on a desktop").isGreaterThan(200);

      page.locator("button:visible:has-text('Összecsukás')").click();
      page.waitForFunction("() => document.getElementById('sidebar').offsetWidth < 100");

      page.reload();
      page.waitForFunction("() => document.getElementById('sidebar').offsetWidth < 100");
      assertThat(sidebarWidth(page)).as("the choice survives a reload").isLessThan(100);

      page.locator("button:visible:has-text('Kinyitás')").click();
      page.waitForFunction("() => document.getElementById('sidebar').offsetWidth > 200");
    }
  }

  @Test
  void theTabBarHidesWhileATextFieldHasFocusOnAPhone() {
    try (BrowserContext context = newContext(390, 844)) {
      Page page = signIn(context);
      page.navigate(BASE_URL + "/households");
      assertThat(page.locator(NAV + ":visible").count()).isEqualTo(1);

      page.locator("#create-name").focus();
      page.locator(NAV + ":visible").waitFor(hidden());

      page.locator("#create-name").blur();
      page.locator(NAV + ":visible").waitFor(visible());
    }
  }

  // ---- helpers ----

  private static BrowserContext newContext(int width, int height) {
    return browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height));
  }

  /** Signs in through the stub provider and lands on the home page. */
  private static Page signIn(BrowserContext context) {
    Page page = context.newPage();
    page.navigate(BASE_URL + "/");
    page.locator("a[href='/oauth2/authorization/google']").click();
    page.waitForURL(url -> URI.create(url).getPath().matches("/(plan|households)"));
    return page;
  }

  private static void createHousehold(Page page, String name) {
    page.locator("#create-name").fill(name);
    page.locator("#create-form button[type=submit]").click();
    page.locator("#households li:has-text('" + name + "')").waitFor();
  }

  /** Chooses a language from the place the device shows it: the sidebar, or the avatar menu. */
  private static void switchLanguage(Page page, String device, String code) {
    if (device.equals("phone")) {
      page.locator(AVATAR_BUTTON).click();
    }
    page.locator("button[lang='" + code + "']:visible").click();
    page.waitForFunction("code => document.documentElement.lang === code", code);
  }

  /** Puts the stored language back to Hungarian, whatever the test did. */
  private static void restoreHungarian(int width, int height) {
    try (BrowserContext context = newContext(width, height)) {
      Page page = signIn(context);
      if ("en".equals(page.getAttribute("html", "lang"))) {
        switchLanguage(page, width < 768 ? "phone" : "desktop", "hu");
      }
    }
  }

  private static String sheetSelector(String device) {
    return "#household-sheet-" + (device.equals("phone") ? "phone" : "desktop");
  }

  private static double sidebarWidth(Page page) {
    BoundingBox box = page.locator("#sidebar").boundingBox();
    return box.width;
  }

  private static Locator.WaitForOptions visible() {
    return new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE);
  }

  private static Locator.WaitForOptions hidden() {
    return new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN);
  }
}
