package hu.lasterbergamot.mealmagician.identity.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The app's front door: who gets in without signing in, what an expired htmx session looks like,
 * CSRF and the security headers. {@code /anything} maps to nothing, so a 404 means "passed the
 * security gate" and a 302/401 means "stopped by it".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityRulesTests {

  @Autowired private MockMvc mvc;

  @Test
  void anonymousBrowserRequestIsRedirectedToTheLoginPage() throws Exception {
    mvc.perform(get("/anything")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
  }

  @Test
  void anonymousHtmxRequestGets401AndAnHxRedirectInsteadOfAFragmentSwap() throws Exception {
    mvc.perform(get("/anything").header("HX-Request", "true"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("HX-Redirect", "/login"));
  }

  @Test
  void healthCheckIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void staticAssetPathsAreNotBehindTheLogin() throws Exception {
    mvc.perform(get("/css/app.css")).andExpect(status().isNotFound());
  }

  @Test
  void signedInUserPassesTheGate() throws Exception {
    mvc.perform(get("/anything").with(oidcLogin())).andExpect(status().isNotFound());
  }

  @Test
  void stateChangingRequestWithoutCsrfTokenIsRejectedEvenWhenSignedIn() throws Exception {
    mvc.perform(post("/anything").with(oidcLogin())).andExpect(status().isForbidden());
  }

  @Test
  void signingOutGoesBackToTheLoginPage() throws Exception {
    mvc.perform(post("/logout").with(oidcLogin()).with(csrf()))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login?logout"));
  }

  @Test
  void googleSignInStartsAtGoogle() throws Exception {
    mvc.perform(get("/oauth2/authorization/google"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
  }

  @Test
  void responsesCannotBeFramedAndLimitTheReferrer() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "same-origin"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }
}
