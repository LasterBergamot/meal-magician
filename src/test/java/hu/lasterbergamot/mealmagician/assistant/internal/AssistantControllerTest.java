package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.assistant.AcceptResult;
import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import hu.lasterbergamot.mealmagician.assistant.DraftClosedException;
import hu.lasterbergamot.mealmagician.assistant.DraftStatus;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdRole;
import hu.lasterbergamot.mealmagician.identity.SignedInUser;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import hu.lasterbergamot.mealmagician.planning.PlanConflictException;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.ui.ExtendedModelMap;

/**
 * The corners of {@link AssistantController} that a browser-level test cannot reach: a replace that
 * lost a race with another edit, an accept that names no mode, and an address that is not an
 * address. The routes themselves are tested in {@code AssistantPagesTest}.
 */
class AssistantControllerTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private final AssistantService assistant = mock(AssistantService.class);
  private final AssistantController controller =
      new AssistantController(
          assistant,
          mock(PlanningService.class),
          new DraftPageFactory(),
          mock(MessageSource.class),
          Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneId.of("Europe/Budapest")));
  private final SignedInUser user =
      new SignedInUser(
          7,
          "hu",
          false,
          new OidcIdToken(
              "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "7")),
          null);
  private final CurrentHousehold household = new CurrentHousehold(3, "Home", HouseholdRole.OWNER);
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final ExtendedModelMap model = new ExtendedModelMap();

  private DraftView draft() {
    return new DraftView(5, MONDAY, DraftStatus.OPEN, "plan", "s", List.of(), Instant.now());
  }

  @Test
  void aReplaceThatLostARaceIsTheConflictBannerWithAPlainReloadLink() {
    when(assistant.draft(7, 3, 5)).thenReturn(draft());
    when(assistant.accept(7, 3, 5, CopyMode.REPLACE)).thenThrow(new PlanConflictException(1));

    String view =
        controller.accept(
            user, household, 5, "REPLACE", request, response, new MockHttpSession(), model);

    assertThat(view).isEqualTo("plan/_conflict");
    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getHeader("HX-Retarget")).isEqualTo("#plan-conflict");
    assertThat(model.getAttribute("reloadHref")).isEqualTo("/plan");
  }

  @Test
  void aDraftAcceptedMeanwhileIsTheConflictBannerThatReloadsItsWeek() {
    when(assistant.draft(7, 3, 5)).thenReturn(draft());
    when(assistant.accept(7, 3, 5, CopyMode.MERGE)).thenThrow(new DraftClosedException(5));

    String view =
        controller.accept(
            user, household, 5, "MERGE", request, response, new MockHttpSession(), model);

    assertThat(view).isEqualTo("plan/_conflict");
    assertThat(model.getAttribute("reloadHref")).isEqualTo("/plan?week=2026-W45");
  }

  @Test
  void anAcceptWithNoModeFillsTheEmptySlots() {
    when(assistant.draft(7, 3, 5)).thenReturn(draft());
    when(assistant.accept(anyLong(), anyLong(), anyLong(), any()))
        .thenReturn(new AcceptResult(2, 1));
    var session = new MockHttpSession();

    String view = controller.accept(user, household, 5, null, request, response, session, model);

    assertThat(view).isNull();
    verify(assistant).accept(7, 3, 5, CopyMode.MERGE);
    assertThat(session.getAttribute(AssistantController.ACCEPTED_ATTRIBUTE)).isEqualTo("2:1");
  }

  @Test
  void anAddressThatIsNotAnAddressIsNotThePageTheBrowserIsOn() {
    assertThat(AssistantController.isSamePage("http://[not an address", "/plan")).isFalse();
    assertThat(AssistantController.isSamePage(null, "/plan")).isFalse();
    assertThat(
            AssistantController.isSamePage(
                "http://localhost/plan?week=2026-W45", "/plan?week=2026-W45"))
        .isTrue();
    assertThat(AssistantController.isSamePage("http://localhost/plan", "/plan?week=2026-W45"))
        .isFalse();
  }
}
