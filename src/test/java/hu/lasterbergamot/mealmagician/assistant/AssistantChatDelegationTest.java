package hu.lasterbergamot.mealmagician.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.assistant.internal.AiProperties;
import hu.lasterbergamot.mealmagician.assistant.internal.ChatService;
import hu.lasterbergamot.mealmagician.assistant.internal.MenuSuggester;
import hu.lasterbergamot.mealmagician.assistant.internal.UsageLedger;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The chat and the occasion menu through {@link AssistantService}: each call goes to the matching
 * logic bean, and while the bean is absent (the assistant is off) each call is refused with {@link
 * AssistantDisabledException}, before any work is done.
 */
class AssistantChatDelegationTest {

  private static final List<ChatTurn> NO_HISTORY = List.of();

  private static AiProperties properties() {
    return new AiProperties(
        true,
        new BigDecimal("5"),
        BigDecimal.ONE,
        BigDecimal.ONE,
        768,
        Duration.ofSeconds(1),
        Duration.ofSeconds(120),
        "food-and-plan");
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  @Test
  void theChatIsHandedToTheChatLogic() {
    var chat = mock(ChatService.class);
    when(chat.reply(1, 2, Locale.ENGLISH, NO_HISTORY, "hi")).thenReturn("hello");
    var service =
        new AssistantService(
            properties(),
            mock(UsageLedger.class),
            mock(HouseholdService.class),
            provider(null),
            provider(chat),
            provider(null));

    assertThat(service.chat(1, 2, Locale.ENGLISH, NO_HISTORY, "hi")).isEqualTo("hello");
  }

  @Test
  void theMenuCallsAreHandedToTheMenuLogic() {
    var menus = mock(MenuSuggester.class);
    var suggestions = new MenuSuggestions(List.of(), List.of());
    when(menus.suggest(1, 2, Locale.ENGLISH, 5)).thenReturn(suggestions);
    when(menus.addIdea(anyLong(), anyLong(), anyLong(), anyString(), anyList(), anyList()))
        .thenReturn("Lecsó");
    var service =
        new AssistantService(
            properties(),
            mock(UsageLedger.class),
            mock(HouseholdService.class),
            provider(null),
            provider(null),
            provider(menus));

    assertThat(service.suggestMenu(1, 2, Locale.ENGLISH, 5)).isSameAs(suggestions);
    assertThat(service.addMenuIdea(1, 2, 5, "Lecsó", List.of("tag"), List.of("paprika")))
        .isEqualTo("Lecsó");
    verify(menus).addIdea(1, 2, 5, "Lecsó", List.of("tag"), List.of("paprika"));
  }

  @Test
  void everyNewCallIsRefusedWhileTheAssistantIsOff() {
    var service =
        new AssistantService(
            properties(),
            mock(UsageLedger.class),
            mock(HouseholdService.class),
            provider(null),
            provider(null),
            provider(null));

    assertThatThrownBy(() -> service.chat(1, 2, Locale.ENGLISH, NO_HISTORY, "hi"))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.suggestMenu(1, 2, Locale.ENGLISH, 5))
        .isInstanceOf(AssistantDisabledException.class);
    assertThatThrownBy(() -> service.addMenuIdea(1, 2, 5, "x", List.of(), List.of()))
        .isInstanceOf(AssistantDisabledException.class);
  }

  @Test
  void aTooLongChatMessageHasItsOwnLimitMessage() {
    var refused = new InvalidRequestException(1001, InvalidRequestException.CHAT_MAX_LENGTH);

    assertThat(refused.getMessage()).isEqualTo("The request is 1001 characters; the limit is 1000");
    assertThat(new InvalidRequestException(7).getMessage())
        .isEqualTo("The request is 7 characters; the limit is 1200");
  }
}
