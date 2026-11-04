package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every request that carries tools must use Spring AI's Google options type with the configured
 * model id (#183). The scripted models of the other tests accept any options type, so this is the
 * only place that notices a request the real Gemini client would reject (a {@code
 * ClassCastException} for the generic options, or a 404 for the SDK's default model).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ToolOptionsTest {

  private static final String MODEL = "gemini-test-model";
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  @Autowired private MealService meals;
  @Autowired private PlanningService planning;
  @Autowired private RatingService ratings;
  @Autowired private OccasionService occasions;
  @Autowired private HouseholdService households;
  @Autowired private MemberService members;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private final FakeChatModel model = new FakeChatModel();
  private ModelGateway gateway;
  private DraftGenerator generator;
  private long anna;
  private long home;
  private long stew;

  @BeforeEach
  void setUp() {
    var properties =
        new AiProperties(
            true,
            new BigDecimal("5.00"),
            BigDecimal.ONE,
            BigDecimal.ONE,
            768,
            Duration.ofSeconds(30),
            Duration.ofSeconds(120),
            "food-and-plan");
    UsageLedger ledger = mock(UsageLedger.class);
    when(ledger.record(anyLong(), anyLong(), anyLong())).thenReturn(1L);
    gateway =
        new ModelGateway(
            model,
            mock(EmbeddingModel.class),
            ledger,
            properties,
            ToolCallingManager.builder().build());
    generator = generator(MODEL);
    anna = HouseholdTestSupport.insertUser(jdbc, "to-anna");
    home = households.create(anna, "Home").id();
    stew = meals.quickAdd(anna, home, "Stew").id();
  }

  @AfterEach
  void closeGateway() {
    gateway.close();
  }

  private DraftGenerator generator(String chatModel) {
    return new DraftGenerator(
        gateway,
        new PromptBuilder(),
        meals,
        members,
        planning,
        ratings,
        occasions,
        mock(EmbeddingIndex.class),
        clock,
        chatModel);
  }

  private void assertGoogleOptionsWithTheModel(Prompt prompt) {
    assertThat(prompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
    var options = (GoogleGenAiChatOptions) prompt.getOptions();
    assertThat(options.getModel()).isEqualTo(MODEL);
    assertThat(options.getToolCallbacks()).isNotEmpty();
  }

  @Test
  void aWeekDraftSendsTheGoogleOptionsWithTheConfiguredModel() {
    model.thenReply(
        "{\"summary\":\"s\",\"items\":[{\"date\":\"%s\",\"slotType\":\"DINNER\",\"mealId\":%d}]}"
            .formatted(MONDAY, stew));

    generator.generate(anna, home, Locale.ENGLISH, MONDAY, List.of(), "a week", null);

    assertGoogleOptionsWithTheModel(model.prompts.getFirst());
  }

  @Test
  void aSlotSuggestionSendsTheGoogleOptionsWithTheConfiguredModel() {
    model.thenReply("{\"picks\":[]}");

    generator.suggestSlot(anna, home, Locale.ENGLISH, MONDAY, SlotType.DINNER);

    assertGoogleOptionsWithTheModel(model.prompts.getFirst());
  }

  @Test
  void aChatReplySendsTheGoogleOptionsWithTheConfiguredModel() throws IOException {
    model.thenReply("Hello");
    var properties =
        new AiProperties(
            true,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            768,
            Duration.ofSeconds(30),
            Duration.ofSeconds(120),
            "food-and-plan");
    var chat =
        new ChatService(gateway, new PromptBuilder(), generator, households, properties, clock);

    chat.reply(anna, home, Locale.ENGLISH, List.of(), "hi");

    assertGoogleOptionsWithTheModel(model.prompts.getFirst());
  }

  @Test
  void aMenuSuggestionSendsTheGoogleOptionsWithTheConfiguredModel() {
    long occasion =
        occasions
            .create(anna, home, new OccasionDetails("Anna", MONDAY, OccasionType.BIRTHDAY, null))
            .id();
    model.thenReply("{\"mealIds\":[],\"ideas\":[]}");
    var suggester =
        new MenuSuggester(gateway, new PromptBuilder(), meals, occasions, generator, clock);

    suggester.suggest(anna, home, Locale.ENGLISH, occasion);

    assertGoogleOptionsWithTheModel(model.prompts.getFirst());
  }

  @Test
  void aBlankModelIdStopsTheStartUp() {
    assertThatThrownBy(() -> generator(" "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.ai.google.genai.chat.model");
    assertThatThrownBy(() -> generator(null)).isInstanceOf(IllegalStateException.class);
  }
}
