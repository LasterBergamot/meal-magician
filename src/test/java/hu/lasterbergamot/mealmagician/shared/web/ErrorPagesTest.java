package hu.lasterbergamot.mealmagician.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.lasterbergamot.mealmagician.shared.i18n.I18nConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MockMvc does not perform the container's error dispatch, so these tests call {@code /error}
 * directly with the request attribute the container would set. {@code RealErrorFlowTest} covers the
 * real dispatch.
 */
@WebSliceTest
class ErrorPagesTest {

  @Autowired private MockMvc mvc;

  private static MockHttpServletRequestBuilder errorWithStatus(int status) {
    return get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status);
  }

  @Test
  void rendersTheNotFoundPage() throws Exception {
    mvc.perform(errorWithStatus(404))
        .andExpect(status().isNotFound())
        .andExpect(content().string(containsString("<html lang=\"hu\"")))
        .andExpect(content().string(matchesPattern("(?s).*<h1[^>]*>Az oldal nem található</h1>.*")))
        .andExpect(content().string(containsString("href=\"/\"")));
  }

  @Test
  void rendersTheForbiddenPage() throws Exception {
    mvc.perform(errorWithStatus(403))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("Nincs jogosultságod")));
  }

  @Test
  void rendersTheServerErrorPage() throws Exception {
    mvc.perform(errorWithStatus(500))
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(containsString("Valami elromlott")));
  }

  @Test
  void treatsOtherStatusesAsServerErrorsInText() throws Exception {
    mvc.perform(errorWithStatus(405))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().string(containsString("Valami elromlott")));
  }

  @Test
  void assumesServerErrorWhenTheContainerGaveNoStatus() throws Exception {
    mvc.perform(get("/error"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().string(containsString("Valami elromlott")));
  }

  @Test
  void rendersTheErrorPageInEnglishWhenAsked() throws Exception {
    mvc.perform(errorWithStatus(404).cookie(new Cookie(I18nConfig.COOKIE_NAME, "en")))
        .andExpect(content().string(containsString("Page not found")));
  }

  @Test
  void answersHtmxRequestsWithAFragmentRoutedIntoTheErrorSlot() throws Exception {
    mvc.perform(errorWithStatus(404).header("HX-Request", "true"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("HX-Retarget", "#error-slot"))
        .andExpect(header().string("HX-Reswap", "innerHTML"))
        .andExpect(header().string("Vary", "HX-Request"))
        .andExpect(content().string(not(containsString("<html"))))
        .andExpect(content().string(containsString("data-kind=\"danger\"")))
        .andExpect(content().string(containsString("role=\"alert\"")))
        .andExpect(content().string(containsString("Az oldal nem található")))
        .andExpect(content().string(containsString("href=\"/\"")))
        .andExpect(content().string(not(containsString("<h1"))));
  }

  @Test
  void leavesForbiddenHtmxRequestsWithoutRetargetSoThePageRedirectsHome() throws Exception {
    mvc.perform(errorWithStatus(403).header("HX-Request", "true"))
        .andExpect(status().isForbidden())
        .andExpect(header().string("HX-Retarget", nullValue()));
  }

  @Test
  void leavesUnauthorizedHtmxRequestsWithoutRetarget() throws Exception {
    mvc.perform(errorWithStatus(401).header("HX-Request", "true"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("HX-Retarget", nullValue()));
  }
}
