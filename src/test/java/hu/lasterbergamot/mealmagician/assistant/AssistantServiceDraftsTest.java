package hu.lasterbergamot.mealmagician.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.assistant.internal.AiProperties;
import hu.lasterbergamot.mealmagician.assistant.internal.DraftService;
import hu.lasterbergamot.mealmagician.assistant.internal.UsageLedger;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.planning.CopyMode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The draft methods of the public service hand over to the draft logic, and refuse to work when it
 * does not exist (the assistant is off). The logic itself is tested with the database in {@code
 * DraftServiceTest}.
 */
class AssistantServiceDraftsTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<DraftService> provider = mock(ObjectProvider.class);

  private final DraftService logic = mock(DraftService.class);
  private final DraftView view =
      new DraftView(5, DAY, DraftStatus.OPEN, "r", "s", java.util.List.of(), null);

  private AssistantService service() {
    var properties =
        new AiProperties(
            true,
            new BigDecimal("5"),
            BigDecimal.ONE,
            BigDecimal.ONE,
            768,
            Duration.ofSeconds(1),
            Duration.ofSeconds(120),
            "food-and-plan");
    return new AssistantService(
        properties,
        mock(UsageLedger.class),
        mock(HouseholdService.class),
        provider,
        mock(ObjectProvider.class),
        mock(ObjectProvider.class));
  }

  @Test
  void everyDraftMethodHandsOverToTheDraftLogic() {
    when(provider.getIfAvailable()).thenReturn(logic);
    when(logic.draftWeek(1, 2, Locale.ENGLISH, DAY, "req")).thenReturn(view);
    when(logic.refine(1, 2, Locale.ENGLISH, 5, "msg")).thenReturn(view);
    when(logic.removeItem(1, 2, 5, 9)).thenReturn(view);
    when(logic.addIdeaToLibrary(1, 2, 5, 9)).thenReturn(view);
    when(logic.accept(1, 2, 5, CopyMode.REPLACE)).thenReturn(new AcceptResult(3, 1));
    when(logic.draft(1, 2, 5)).thenReturn(view);
    var service = service();

    assertThat(service.draftWeek(1, 2, Locale.ENGLISH, DAY, "req")).isSameAs(view);
    assertThat(service.refine(1, 2, Locale.ENGLISH, 5, "msg")).isSameAs(view);
    assertThat(service.removeItem(1, 2, 5, 9)).isSameAs(view);
    assertThat(service.addIdeaToLibrary(1, 2, 5, 9)).isSameAs(view);
    assertThat(service.accept(1, 2, 5, CopyMode.REPLACE)).isEqualTo(new AcceptResult(3, 1));
    assertThat(service.draft(1, 2, 5)).isSameAs(view);
    service.discard(1, 2, 5);
    verify(logic).discard(1, 2, 5);
  }

  @Test
  void whileTheAssistantIsOffEveryDraftMethodIsA404() {
    when(provider.getIfAvailable()).thenReturn(null);
    var service = service();

    assertThatThrownBy(() -> service.draftWeek(1, 2, Locale.ENGLISH, DAY, "r"))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.refine(1, 2, Locale.ENGLISH, 5, "m"))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.removeItem(1, 2, 5, 9))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.addIdeaToLibrary(1, 2, 5, 9))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.accept(1, 2, 5, CopyMode.MERGE))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.discard(1, 2, 5))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.draft(1, 2, 5)).isInstanceOf(AssistantDisabledException.class);
  }

  @Test
  void theDraftExceptionsCarryTheirIdsAndNoPrompt() {
    assertThat(new DraftNotFoundException(5)).hasMessageContaining("5");
    assertThat(new DraftNotFoundException(5, 9))
        .hasMessageContaining("9")
        .hasMessageContaining("5");
    assertThat(new DraftClosedException(5)).hasMessageContaining("no longer open");
    assertThat(new InvalidRequestException(1500))
        .hasMessageContaining("1500")
        .hasMessageContaining("1200");
    assertThat(new DraftGenerationException("x")).hasMessageContaining("x");
    assertThat(
            new DraftItemView(
                    1,
                    DAY,
                    hu.lasterbergamot.mealmagician.planning.SlotType.LUNCH,
                    0,
                    3L,
                    "n",
                    null,
                    null,
                    java.util.List.of(),
                    java.util.List.of())
                .idea())
        .isFalse();
  }
}
