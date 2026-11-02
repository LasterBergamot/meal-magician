package hu.lasterbergamot.mealmagician.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.shared.i18n.Messages;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class ViewContextAdviceTest {

  private final Messages messages = mock(Messages.class);

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> absent() {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> present(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }

  @Test
  void anAnonymousRequestGetsNoShellDataAndNoHighlight() {
    var advice = new ViewContextAdvice(messages, absent(), absent(), absent());

    ViewContext ctx = advice.viewContext(new MockHttpServletRequest("GET", "/login"));

    assertThat(ctx.user()).isNull();
    assertThat(ctx.household()).isNull();
    assertThat(ctx.csrf()).isNull();
    assertThat(ctx.admin()).isFalse();
    assertThat(ctx.active()).isNull();
    assertThat(ctx.theme()).isEqualTo(Theme.SYSTEM);
    assertThat(ctx.messages()).isSameAs(messages);
  }

  @Test
  void aSignedInRequestGetsTheSourcesDataAndTheHighlightOfItsPage() {
    var shellUser = new ShellUser("Anna Kovács");
    var household = new HouseholdNav("Annas", List.of());
    var csrf = new CsrfMeta("X-CSRF-TOKEN", "token");
    var advice =
        new ViewContextAdvice(
            messages,
            present((CsrfMetaSource) request -> csrf),
            present((HouseholdNavSource) request -> household),
            present((ShellUserSource) request -> shellUser));

    ViewContext ctx = advice.viewContext(new MockHttpServletRequest("GET", "/households"));

    assertThat(ctx.user()).isSameAs(shellUser);
    assertThat(ctx.household()).isSameAs(household);
    assertThat(ctx.csrf()).isSameAs(csrf);
    assertThat(ctx.active()).isEqualTo(NavDestination.HOUSEHOLD);
  }

  @Test
  void theThemeComesFromTheThemeCookie() {
    var advice = new ViewContextAdvice(messages, absent(), absent(), absent());
    var request = new MockHttpServletRequest("GET", "/");
    request.setCookies(new Cookie("other", "x"), new Cookie(Theme.COOKIE_NAME, "dark"));

    assertThat(advice.viewContext(request).theme()).isEqualTo(Theme.DARK);
  }

  @Test
  void anUnknownThemeCookieMeansSystem() {
    var advice = new ViewContextAdvice(messages, absent(), absent(), absent());
    var request = new MockHttpServletRequest("GET", "/");
    request.setCookies(new Cookie(Theme.COOKIE_NAME, "purple"));

    assertThat(advice.viewContext(request).theme()).isEqualTo(Theme.SYSTEM);
  }

  @Test
  void requestsWithOtherCookiesOnlyGetTheDefaultTheme() {
    var advice = new ViewContextAdvice(messages, absent(), absent(), absent());
    var request = new MockHttpServletRequest("GET", "/");
    request.setCookies(new Cookie("mm_lang", "en"));

    assertThat(advice.viewContext(request).theme()).isEqualTo(Theme.SYSTEM);
  }
}
