package hu.lasterbergamot.mealmagician.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LanguageControllerTest {

  private final I18nConfig config = new I18nConfig();
  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/language");
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @SuppressWarnings("unchecked")
  private static ObjectProvider<LanguageStore> providing(LanguageStore store) {
    ObjectProvider<LanguageStore> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(store);
    return provider;
  }

  private LanguageController controller(LanguageStore store) {
    return new LanguageController(config.localeResolver(), providing(store));
  }

  private String switchTo(String code, LanguageStore store, String referer) {
    if (referer != null) {
      request.addHeader(HttpHeaders.REFERER, referer);
    }
    var answer = controller(store).switchLanguage(code, request, response);
    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    return answer.getHeaders().getFirst(HttpHeaders.LOCATION);
  }

  @Test
  void aSwitchSetsTheCookieAndSavesTheChoiceForASignedInPerson() {
    var store = mock(LanguageStore.class);

    switchTo("en", store, null);

    assertThat(response.getCookie(I18nConfig.COOKIE_NAME).getValue()).isEqualTo("en");
    verify(store).remember(eq(request), eq(Locale.of("en")));
  }

  @Test
  void aSwitchWithoutAStoreStillSetsTheCookie() {
    switchTo("hu", null, null);

    assertThat(response.getCookie(I18nConfig.COOKIE_NAME).getValue()).isEqualTo("hu");
  }

  @Test
  void anUnsupportedCodeChangesNothing() {
    var store = mock(LanguageStore.class);

    String location = switchTo("de", store, null);

    assertThat(location).isEqualTo("/");
    assertThat(response.getCookie(I18nConfig.COOKIE_NAME)).isNull();
    verifyNoInteractions(store);
  }

  @Test
  void theBrowserGoesBackToThePageItCameFromWithItsQuery() {
    assertThat(switchTo("en", null, "http://localhost/households?x=1"))
        .isEqualTo("/households?x=1");
  }

  @Test
  void aRefererWithoutAQueryKeepsJustThePath() {
    assertThat(switchTo("en", null, "http://localhost/admin/allowlist"))
        .isEqualTo("/admin/allowlist");
  }

  @Test
  void aMissingRefererGoesHome() {
    assertThat(switchTo("en", null, null)).isEqualTo("/");
  }

  @Test
  void aRefererFromAnotherSiteGoesHome() {
    assertThat(switchTo("en", null, "https://evil.example/phish")).isEqualTo("/");
  }

  @Test
  void aProtocolRelativePathIsNeverUsedAsTheTarget() {
    assertThat(switchTo("en", null, "http://localhost//evil.example/x")).isEqualTo("/");
  }

  @Test
  void anEncodedBackslashStaysEncodedInTheTarget() {
    assertThat(switchTo("en", null, "http://localhost/a%5Cb")).isEqualTo("/a%5Cb");
  }

  @Test
  void aMalformedRefererGoesHome() {
    assertThat(switchTo("en", null, "http://localhost/ bad path")).isEqualTo("/");
  }

  @Test
  void aRawBackslashInTheRefererGoesHome() {
    assertThat(switchTo("en", null, "http://localhost/a\\b")).isEqualTo("/");
  }

  @Test
  void aRefererWithAnEmptyPathGoesHome() {
    assertThat(switchTo("en", null, "http://localhost")).isEqualTo("/");
  }

  @Test
  void aRefererWithoutAPathGoesHome() {
    assertThat(switchTo("en", null, "mailto:someone@localhost")).isEqualTo("/");
  }
}
