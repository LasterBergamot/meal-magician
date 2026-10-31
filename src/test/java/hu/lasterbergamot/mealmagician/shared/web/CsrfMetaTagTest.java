package hu.lasterbergamot.mealmagician.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The layout writes CSRF meta tags only when a {@link CsrfMetaSource} bean supplies a token. */
@WebSliceTest
@Import(CsrfMetaTagTest.TokenSource.class)
class CsrfMetaTagTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class TokenSource {
    @Bean
    CsrfMetaSource csrfMetaSource() {
      // Requests with the "no-token" header model a request the security layer gave no token.
      return request ->
          request.getHeader("no-token") != null ? null : new CsrfMeta("X-CSRF-TOKEN", "abc123");
    }
  }

  @Autowired private MockMvc mvc;

  @Test
  void rendersTheTokenAndHeaderNameAsMetaTags() throws Exception {
    mvc.perform(get("/"))
        .andExpect(content().string(containsString("<meta name=\"_csrf\" content=\"abc123\">")))
        .andExpect(
            content()
                .string(containsString("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\">")));
  }

  @Test
  void rendersNoMetaTagsForARequestWithoutToken() throws Exception {
    mvc.perform(get("/").header("no-token", "1"))
        .andExpect(content().string(not(containsString("name=\"_csrf\""))));
  }
}
