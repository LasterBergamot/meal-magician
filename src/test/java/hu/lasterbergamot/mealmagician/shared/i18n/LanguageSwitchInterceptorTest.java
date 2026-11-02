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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

class LanguageSwitchInterceptorTest {

  @SuppressWarnings("unchecked")
  private static ObjectProvider<LanguageStore> providing(LanguageStore store) {
    ObjectProvider<LanguageStore> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(store);
    return provider;
  }

  private static MockHttpServletRequest requestWithLanguage(String code) {
    var request = new MockHttpServletRequest("GET", "/");
    request.setParameter(LanguageSwitchInterceptor.PARAMETER, code);
    request.setAttribute(
        DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,
        new CookieLocaleResolver(I18nConfig.COOKIE_NAME));
    return request;
  }

  @Test
  void aSwitchSetsTheCookieAndSavesTheChoiceForASignedInPerson() throws Exception {
    var store = mock(LanguageStore.class);
    var request = requestWithLanguage("en");
    var response = new MockHttpServletResponse();

    boolean proceed =
        new LanguageSwitchInterceptor(providing(store)).preHandle(request, response, null);

    assertThat(proceed).isTrue();
    assertThat(response.getCookie(I18nConfig.COOKIE_NAME).getValue()).isEqualTo("en");
    verify(store).remember(eq(request), eq(Locale.of("en")));
  }

  @Test
  void aSwitchWithoutAStoreStillSetsTheCookie() throws Exception {
    var request = requestWithLanguage("hu");
    var response = new MockHttpServletResponse();

    new LanguageSwitchInterceptor(providing(null)).preHandle(request, response, null);

    assertThat(response.getCookie(I18nConfig.COOKIE_NAME).getValue()).isEqualTo("hu");
  }

  @Test
  void anUnsupportedCodeChangesNothing() throws Exception {
    var store = mock(LanguageStore.class);
    var response = new MockHttpServletResponse();

    new LanguageSwitchInterceptor(providing(store))
        .preHandle(requestWithLanguage("de"), response, null);

    assertThat(response.getCookie(I18nConfig.COOKIE_NAME)).isNull();
    verifyNoInteractions(store);
  }

  @Test
  void aRequestWithoutTheParameterChangesNothing() throws Exception {
    var store = mock(LanguageStore.class);
    var request = new MockHttpServletRequest("GET", "/");
    var response = new MockHttpServletResponse();

    assertThat(new LanguageSwitchInterceptor(providing(store)).preHandle(request, response, null))
        .isTrue();
    assertThat(response.getCookie(I18nConfig.COOKIE_NAME)).isNull();
    verifyNoInteractions(store);
  }
}
