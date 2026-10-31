package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The installable-app files (D5): manifest, icons, service worker, and the vendored scripts. */
@WebSliceTest
class PwaAssetsTest {

  @Autowired private MockMvc mvc;

  @Test
  void servesTheManifestAsManifestJson() throws Exception {
    mvc.perform(get("/manifest.webmanifest"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/manifest+json"));
  }

  @Test
  void manifestDeclaresAnInstallableApp() throws Exception {
    String body =
        mvc.perform(get("/manifest.webmanifest")).andReturn().getResponse().getContentAsString();

    JsonNode manifest = new ObjectMapper().readTree(body);
    assertThat(manifest.get("display").asText()).isEqualTo("standalone");
    assertThat(manifest.get("start_url").asText()).isEqualTo("/");
    assertThat(manifest.get("theme_color").asText()).isEqualTo("#15803d");
    List<String> purposes = new ArrayList<>();
    for (JsonNode icon : manifest.get("icons")) {
      purposes.add(icon.get("sizes").asText() + " " + icon.get("purpose").asText());
      mvc.perform(get(icon.get("src").asText()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }
    assertThat(purposes).contains("192x192 any", "512x512 any", "512x512 maskable");
  }

  @Test
  void servesTheServiceWorkerAsJavaScriptFromTheRoot() throws Exception {
    String javascript =
        mvc.perform(get("/sw.js"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentType();

    assertThat(javascript).containsAnyOf("text/javascript", "application/javascript");
  }

  @Test
  void serviceWorkerCachesStaticFilesOnly() throws Exception {
    String worker = mvc.perform(get("/sw.js")).andReturn().getResponse().getContentAsString();

    assertThat(worker).contains("css|js|icons").doesNotContain("text/html");
  }

  @Test
  void servesTheVendoredScriptsAndTheIconForIos() throws Exception {
    for (String path :
        List.of(
            "/js/htmx.min.js",
            "/js/alpine-csp.min.js",
            "/js/app.js",
            "/js/sw-register.js",
            "/icons/apple-touch-icon.png")) {
      mvc.perform(get(path)).andExpect(status().isOk());
    }
  }
}
