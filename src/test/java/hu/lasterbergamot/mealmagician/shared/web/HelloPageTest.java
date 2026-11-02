package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.shared.i18n.I18nConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebSliceTest
class HelloPageTest {

  @Autowired private MockMvc mvc;

  @Test
  void rendersTheFullPageInHungarianByDefault() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(
            content().string(org.hamcrest.Matchers.matchesPattern("(?s)\\s*<!DOCTYPE html>.*")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"hu\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Szia, Meal Magician!")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Ma 2026. október 31., szombat")))
        // No one is signed in in this slice, so the page is the anonymous layout with the language
        // choice in its header; the app shell is covered by AppShellPagesTest.
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("name=\"lang\" value=\"en\"")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"shell\""))))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/app.css")))
        .andExpect(header().string("Vary", "HX-Request"));
  }

  @Test
  void rendersOnlyTheFragmentForHtmxRequests() throws Exception {
    mvc.perform(get("/").header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.matchesPattern("(?s)\\s*<div id=\"greeting\".*")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))))
        .andExpect(header().string("Vary", "HX-Request"));
  }

  @Test
  void ignoresTheBrowsersAcceptLanguageBecauseHungarianIsTheDefault() throws Exception {
    mvc.perform(get("/").header("Accept-Language", "en-US,en;q=0.9"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"hu\"")));
  }

  @Test
  void rendersEnglishWhenTheRememberedLanguageIsEnglish() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(I18nConfig.COOKIE_NAME, "en")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"en\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, Meal Magician!")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString("Today is Saturday, October 31, 2026.")));
  }

  @Test
  void usesTheRememberedLanguageOnLaterRequests() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(I18nConfig.COOKIE_NAME, "en")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"en\"")));
  }

  @Test
  void aLangQueryParameterChangesNothingBecauseTheSwitchIsAPost() throws Exception {
    MvcResult result =
        mvc.perform(get("/").param("lang", "en"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"hu\"")))
            .andReturn();

    assertThat(result.getResponse().getCookie(I18nConfig.COOKIE_NAME)).isNull();
  }

  @Test
  void fallsBackToHungarianForATamperedCookie() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(I18nConfig.COOKIE_NAME, "de")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"hu\"")));
  }

  @Test
  void marksTheCurrentLanguageInTheSwitcher() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(I18nConfig.COOKIE_NAME, "en")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.matchesPattern(
                        "(?s).*lang=\"en\" aria-label=\"English\"\\s+aria-current=\"true\".*")));
  }

  @Test
  void theHtmlElementCarriesTheThemeFromTheCookie() throws Exception {
    mvc.perform(get("/"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-theme=\"system\"")));
    for (String theme : new String[] {"light", "dark", "system"}) {
      mvc.perform(get("/").cookie(new Cookie(Theme.COOKIE_NAME, theme)))
          .andExpect(
              content()
                  .string(org.hamcrest.Matchers.containsString("data-theme=\"" + theme + "\"")));
    }
  }

  @Test
  void anUnknownThemeCookieFallsBackToSystem() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(Theme.COOKIE_NAME, "\"><script>")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-theme=\"system\"")));
  }

  @Test
  void anExplicitThemeLeavesOnlyItsOwnThemeColorMetaOn() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(Theme.COOKIE_NAME, "dark")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "data-theme-color=\"light\" media=\"not all\"")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "data-theme-color=\"dark\" media=\"all\"")));
    mvc.perform(get("/"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "data-theme-color=\"light\" media=\"(prefers-color-scheme: light)\"")));
  }
}
