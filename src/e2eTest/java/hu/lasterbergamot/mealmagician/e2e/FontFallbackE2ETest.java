package hu.lasterbergamot.mealmagician.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves in a real browser that a heading with Hungarian ő and ű is drawn by Baloo 2 alone (UI-D4,
 * #130). Baloo 2 has Latin Extended-A glyphs, so the browser must download its latin-ext file at
 * the heading weight (700) and no other family may draw a letter. The stylesheet-level check is
 * {@code FontFallbackTest}; this one asks Chromium which fonts it really used.
 *
 * <p>The login page is public, so no sign-in is needed. The heading is added by a script run from
 * the test (the CSP only restricts the page's own markup), because no template uses the display
 * font yet.
 */
class FontFallbackE2ETest {

  private static final String BASE_URL =
      System.getProperty("e2e.baseUrl", "http://localhost:18080");

  /**
   * Eleven characters with the space: seven of Latin-1 (F é t e l h t) and three of Latin
   * Extended-A (ő ű ő).
   */
  private static final String HEADING = "Főétel hűtő";

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  @AfterAll
  static void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @Test
  void headingWithDoubleAcuteLettersIsDrawnByBaloo2Only() {
    try (BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      List<String> served = new ArrayList<>();
      page.onResponse(response -> served.add(response.status() + " " + response.url()));
      page.navigate(BASE_URL + "/login");

      page.evaluate(
          """
          async (text) => {
            const heading = document.createElement('h2');
            heading.id = 'font-probe';
            heading.textContent = text;
            heading.style.fontFamily = 'var(--font-display)';
            heading.style.fontWeight = '700';
            document.body.append(heading);
            await document.fonts.ready;
          }
          """,
          HEADING);

      JsonArray fonts = platformFontsOf(context, page, "#font-probe");

      assertThat(glyphsDrawnBy(fonts, "Baloo 2"))
          .as("glyphs drawn by Baloo 2 in %s", fonts)
          .isEqualTo(HEADING.length());
      assertThat(glyphsDrawnBy(fonts, "Nunito")).as("glyphs drawn by Nunito in %s", fonts).isZero();
      assertThat(served)
          .as("the latin-ext file is downloaded from our own server")
          .anyMatch(
              response -> response.equals("200 " + BASE_URL + "/fonts/baloo2-latin-ext.woff2"));
    }
  }

  /** The fonts Chromium used to draw the element, as the DevTools protocol reports them. */
  private static JsonArray platformFontsOf(BrowserContext context, Page page, String selector) {
    CDPSession cdp = context.newCDPSession(page);
    cdp.send("DOM.enable");
    cdp.send("CSS.enable");
    int root = cdp.send("DOM.getDocument").getAsJsonObject("root").get("nodeId").getAsInt();
    JsonObject query = new JsonObject();
    query.addProperty("nodeId", root);
    query.addProperty("selector", selector);
    JsonObject node = new JsonObject();
    node.addProperty("nodeId", cdp.send("DOM.querySelector", query).get("nodeId").getAsInt());
    return cdp.send("CSS.getPlatformFontsForNode", node).getAsJsonArray("fonts");
  }

  /** Total glyphs drawn by the downloaded (custom) fonts whose family starts with the name. */
  private static int glyphsDrawnBy(JsonArray fonts, String family) {
    int glyphs = 0;
    for (JsonElement element : fonts) {
      JsonObject font = element.getAsJsonObject();
      if (font.get("familyName").getAsString().startsWith(family)
          && font.get("isCustomFont").getAsBoolean()) {
        glyphs += font.get("glyphCount").getAsInt();
      }
    }
    return glyphs;
  }
}
