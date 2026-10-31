package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the whole app on a real port, because MockMvc skips the servlet container's error dispatch:
 * this proves an unknown URL really ends up on our error page, with security headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class RealErrorFlowTest {

  @LocalServerPort private int port;

  private HttpResponse<String> get(String path, String... headers) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (headers.length > 0) {
      request.headers(headers);
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void unknownUrlShowsTheNotFoundPage() throws Exception {
    HttpResponse<String> response = get("/no-such-page");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).contains("<html lang=\"hu\"").contains("Az oldal nem található");
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
    assertThat(response.headers().firstValue("Content-Security-Policy"))
        .contains(SecurityHeadersFilter.CONTENT_SECURITY_POLICY);
  }

  @Test
  void unknownUrlFromHtmxGetsTheFragmentForTheErrorSlot() throws Exception {
    HttpResponse<String> response = get("/no-such-page", "HX-Request", "true");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).doesNotContain("<html").contains("Az oldal nem található");
    assertThat(response.headers().firstValue("HX-Retarget")).contains("#error-slot");
  }

  @Test
  void helloPageIsServedOnARealPort() throws Exception {
    HttpResponse<String> response = get("/");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("Szia, Meal Magician!");
  }
}
