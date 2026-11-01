package hu.lasterbergamot.mealmagician.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.household.internal.CurrentHouseholdResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

/** How the current household is chosen, remembered and re-checked (no database needed). */
class CurrentHouseholdResolverTest {

  private static final long USER = 5L;

  private final HouseholdService households = mock(HouseholdService.class);
  private final CurrentHouseholdResolver resolver = new CurrentHouseholdResolver(households);
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  private final CurrentHousehold home = new CurrentHousehold(1, "Home", HouseholdRole.OWNER);
  private final CurrentHousehold cabin = new CurrentHousehold(2, "Cabin", HouseholdRole.MEMBER);

  @BeforeEach
  void signIn() {
    var user = HouseholdTestSupport.signedIn(USER);
    SecurityContextHolder.getContext()
        .setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
  }

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void anonymousRequestsHaveNoHousehold() {
    SecurityContextHolder.clearContext();

    assertThat(resolver.resolve(request)).isEmpty();
    verify(households, never()).listFor(USER);
  }

  @Test
  void aPrincipalThatIsNotASignedInUserHasNoHousehold() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "n/a"));

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void usesTheHouseholdChosenInTheSession() {
    resolver.select(request, 2);
    when(households.find(2, USER)).thenReturn(Optional.of(cabin));

    assertThat(resolver.resolve(request)).contains(cabin);
    verify(households, never()).listFor(USER);
  }

  @Test
  void aChoiceThePersonNoLongerBelongsToFallsBackToTheFirstHousehold() {
    resolver.select(request, 99);
    when(households.find(99, USER)).thenReturn(Optional.empty());
    when(households.listFor(USER))
        .thenReturn(List.of(new HouseholdSummary(1, "Home", HouseholdRole.OWNER)));

    assertThat(resolver.resolve(request)).contains(home);
    assertThat(request.getSession().getAttribute("mm.currentHouseholdId")).isEqualTo(1L);
  }

  @Test
  void withNoChoiceTheFirstHouseholdIsUsedAndRemembered() {
    when(households.listFor(USER))
        .thenReturn(
            List.of(
                new HouseholdSummary(1, "Home", HouseholdRole.OWNER),
                new HouseholdSummary(2, "Cabin", HouseholdRole.MEMBER)));

    assertThat(resolver.resolve(request)).contains(home);
    assertThat(request.getSession().getAttribute("mm.currentHouseholdId")).isEqualTo(1L);
  }

  @Test
  void aPersonWithoutHouseholdsHasNone() {
    when(households.listFor(USER)).thenReturn(List.of());

    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void aTamperedSessionValueIsIgnored() {
    request.getSession().setAttribute("mm.currentHouseholdId", "not a number");
    when(households.listFor(USER))
        .thenReturn(List.of(new HouseholdSummary(1, "Home", HouseholdRole.OWNER)));

    assertThat(resolver.resolve(request)).contains(home);
  }

  @Test
  void theAnswerIsLookedUpOncePerRequest() {
    when(households.listFor(USER))
        .thenReturn(List.of(new HouseholdSummary(1, "Home", HouseholdRole.OWNER)));

    resolver.resolve(request);
    resolver.resolve(request);

    verify(households, times(1)).listFor(USER);
  }

  @Test
  void selectingAHouseholdDropsTheCachedAnswer() {
    when(households.listFor(USER))
        .thenReturn(List.of(new HouseholdSummary(1, "Home", HouseholdRole.OWNER)));
    when(households.find(2, USER)).thenReturn(Optional.of(cabin));
    resolver.resolve(request);

    resolver.select(request, 2);

    assertThat(resolver.resolve(request)).contains(cabin);
  }
}
