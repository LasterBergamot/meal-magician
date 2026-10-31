package hu.lasterbergamot.mealmagician.identity.internal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The sign-in page as an anonymous visitor and as a signed-in person see it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LoginPageTest {

  @Autowired private MockMvc mvc;

  @Test
  void anonymousVisitorSeesTheGoogleButtonAndNoNotice() throws Exception {
    mvc.perform(get("/login").param("lang", "en"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/oauth2/authorization/google\"")))
        .andExpect(content().string(containsString("Sign in with Google")))
        .andExpect(content().string(not(containsString("Signing in did not work."))))
        .andExpect(content().string(not(containsString("You have been signed out."))));
  }

  @Test
  void loginPageCarriesTheCsrfMetaTagsForLaterHtmxRequests() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(content().string(containsString("<meta name=\"_csrf\"")))
        .andExpect(content().string(containsString("<meta name=\"_csrf_header\"")));
  }

  @Test
  void failedSignInShowsAnErrorNotice() throws Exception {
    mvc.perform(get("/login").param("error", "").param("lang", "en"))
        .andExpect(content().string(containsString("Signing in did not work.")));
  }

  @Test
  void signingOutShowsAConfirmation() throws Exception {
    mvc.perform(get("/login").param("logout", "").param("lang", "en"))
        .andExpect(content().string(containsString("You have been signed out.")));
  }

  @Test
  void signedInPersonIsSentToTheHomePage() throws Exception {
    mvc.perform(get("/login").with(oidcLogin()))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void signedInPersonSeesTheHomePage() throws Exception {
    mvc.perform(get("/").with(oidcLogin())).andExpect(status().isOk());
  }

  @Test
  void loginPageHasTheSecurityHeaders() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Content-Security-Policy"));
  }
}
