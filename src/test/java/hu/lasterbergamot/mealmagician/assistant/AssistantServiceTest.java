package hu.lasterbergamot.mealmagician.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import hu.lasterbergamot.mealmagician.assistant.internal.AiProperties;
import hu.lasterbergamot.mealmagician.assistant.internal.UsageLedger;
import hu.lasterbergamot.mealmagician.household.HouseholdNotFoundException;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The assistant's public API, and the proof that with the default settings the assistant is inert:
 * no model, no gateway and no Google client exist, yet the application context starts without any
 * Google credentials.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AssistantServiceTest {

  @Autowired private AssistantService assistant;
  @Autowired private UsageLedger ledger;
  @Autowired private HouseholdService households;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationContext context;

  @Autowired
  private ObjectProvider<hu.lasterbergamot.mealmagician.assistant.internal.DraftService> drafts;

  private long anna;
  private long cili;
  private long annas;

  @BeforeEach
  void people() {
    anna = HouseholdTestSupport.insertUser(jdbc, "svc-anna");
    cili = HouseholdTestSupport.insertUser(jdbc, "svc-cili");
    annas = households.create(anna, "Svc Annas").id();
  }

  private static <T> ObjectProvider<T> absentProvider() {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    return provider;
  }

  private AssistantService enabledService() {
    var on =
        new AiProperties(
            true,
            new BigDecimal("5.00"),
            BigDecimal.ONE,
            BigDecimal.ONE,
            768,
            Duration.ofSeconds(1),
            Duration.ofSeconds(120),
            "food-and-plan");
    return new AssistantService(on, ledger, households, drafts, absentProvider(), absentProvider());
  }

  @Test
  void byDefaultTheAssistantIsOffAndNothingThatCallsOutExists() {
    assertThat(assistant.enabled()).isFalse();
    assertThat(context.getBeanNamesForType(org.springframework.ai.chat.model.ChatModel.class))
        .isEmpty();
    assertThat(context.getBeanNamesForType(org.springframework.ai.embedding.EmbeddingModel.class))
        .isEmpty();
    assertThat(context.getBeanNamesForType(com.google.genai.Client.class)).isEmpty();
    assertThat(
            context.getBeanNamesForType(
                hu.lasterbergamot.mealmagician.assistant.internal.ModelGateway.class))
        .isEmpty();
  }

  @Test
  void usageWhileOffIsA404() {
    assertThatThrownBy(() -> assistant.usageThisMonth(anna, annas))
        .isInstanceOf(AssistantDisabledException.class);
  }

  @Test
  void aMemberSeesTheHouseholdsUsageThisMonth() {
    var usage = enabledService().usageThisMonth(anna, annas);

    assertThat(enabledService().enabled()).isTrue();
    assertThat(usage.month())
        .isEqualTo(
            LocalDate.now(hu.lasterbergamot.mealmagician.shared.ClockConfig.ZONE)
                .withDayOfMonth(1));
    assertThat(usage.costMicroUsd()).isZero();
    assertThat(usage.capMicroUsd()).isEqualTo(5_000_000L);
    assertThat(usage.capReached()).isFalse();
  }

  @Test
  void aPersonOutsideTheHouseholdGetsA404() {
    assertThatThrownBy(() -> enabledService().usageThisMonth(cili, annas))
        .isInstanceOf(HouseholdNotFoundException.class);
  }

  @Test
  void capReachedIsTrueAtAndOverTheCap() {
    var month = LocalDate.of(2026, 11, 1);
    assertThat(new MonthlyUsage(month, 0, 0, 99, 100).capReached()).isFalse();
    assertThat(new MonthlyUsage(month, 0, 0, 100, 100).capReached()).isTrue();
    assertThat(new MonthlyUsage(month, 0, 0, 101, 100).capReached()).isTrue();
  }

  @Test
  void theExceptionsCarryNoPromptAndTheRightStatus() {
    var capped = new SpendCapExceededException(7, 5);
    assertThat(capped.usedMicroUsd()).isEqualTo(7);
    assertThat(capped.capMicroUsd()).isEqualTo(5);
    assertThat(capped).hasMessageContaining("7 of 5");
    assertThat(new ModelUnavailableException("timed out"))
        .hasMessage("The AI model is unavailable: timed out");
    assertThat(new AssistantDisabledException()).hasMessageContaining("switched off");
  }
}
