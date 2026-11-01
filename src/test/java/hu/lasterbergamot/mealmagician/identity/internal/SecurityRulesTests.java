package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
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
    mvc.perform(get("/css/no-such-file.css")).andExpect(status().isNotFound());
  }

  /**
   * A static file behind the login does more harm than a broken image: Spring Security remembers
   * the last request it turned away and sends the person there after signing in, so an anonymous
   * font request on the login page would land them on the font file instead of the home page (what
   * broke the sign-in E2E tests when {@code /fonts} was added without being public).
   */
  @Test
  void everyStaticFileIsServedWithoutSigningIn() throws Exception {
    Path root = Path.of("src/main/resources/static");
    List<String> paths;
    try (Stream<Path> files = Files.walk(root)) {
      paths =
          files
              .filter(Files::isRegularFile)
              .map(file -> "/" + root.relativize(file).toString().replace('\\', '/'))
              .toList();
    }

    assertThat(paths).contains("/fonts/fredoka-latin.woff2", "/icons/favicon.svg", "/js/app.js");
    for (String path : paths) {
      mvc.perform(get(path)).andExpect(status().isOk());
    }
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
