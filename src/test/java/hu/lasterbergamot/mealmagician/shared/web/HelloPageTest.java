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
        .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"household-slot\"")))
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
  void switchesToEnglishAndRemembersTheChoiceInACookie() throws Exception {
    MvcResult result =
        mvc.perform(get("/").param("lang", "en"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"en\"")))
            .andExpect(
                content().string(org.hamcrest.Matchers.containsString("Hello, Meal Magician!")))
            .andExpect(
                content()
                    .string(
                        org.hamcrest.Matchers.containsString(
                            "Today is Saturday, October 31, 2026.")))
            .andReturn();

    Cookie cookie = result.getResponse().getCookie(I18nConfig.COOKIE_NAME);
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo("en");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getMaxAge()).isEqualTo(365 * 24 * 60 * 60);
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  void usesTheRememberedLanguageOnLaterRequests() throws Exception {
    mvc.perform(get("/").cookie(new Cookie(I18nConfig.COOKIE_NAME, "en")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"en\"")));
  }

  @Test
  void switchesBackToHungarian() throws Exception {
    mvc.perform(get("/").param("lang", "hu").cookie(new Cookie(I18nConfig.COOKIE_NAME, "en")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"hu\"")));
  }

  @Test
  void ignoresUnsupportedLanguageCodes() throws Exception {
    MvcResult result =
        mvc.perform(get("/").param("lang", "de"))
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
    mvc.perform(get("/").param("lang", "en"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.matchesPattern(
                        "(?s).*hreflang=\"en\"[^>]*aria-current=\"true\".*")));
  }
}
