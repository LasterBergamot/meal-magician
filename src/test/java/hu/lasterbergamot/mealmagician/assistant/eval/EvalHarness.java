package hu.lasterbergamot.mealmagician.assistant.eval;

import hu.lasterbergamot.mealmagician.MealMagicianApplication;
import hu.lasterbergamot.mealmagician.assistant.AssistantService;
import hu.lasterbergamot.mealmagician.assistant.DraftGenerationException;
import hu.lasterbergamot.mealmagician.assistant.MonthlyUsage;
import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealSearch;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.feedback.RatingInput;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.feedback.Thumb;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.household.InviteExpiry;
import hu.lasterbergamot.mealmagician.household.InviteService;
import hu.lasterbergamot.mealmagician.persistence.AbstractPostgresIntegrationTest;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The assistant for one model, on a throw-away database. It starts the real application (its web
 * layer listens on a random local port that nothing uses) on the Testcontainers Postgres of the
 * integration tests, creates a made-up household through the real services (never the scripted fake
 * model, never real household data), and then runs cases through {@link AssistantService}, the same
 * door the pages use. So the prompts, the tools, the embedding index, the validator and the retry
 * are all the product's own; only the clock is moved from case to case.
 *
 * <p><strong>Cost is metered through the ledger.</strong> The tokens and the cost of a run are the
 * difference in the household's {@code assistant_usage} row before and after the run, so they are
 * what {@code ModelGateway} recorded, at the price given for this model. The ledger's own monthly
 * cap is set so high that it never refuses a call; the runner's spend limit ({@code --budget-usd})
 * is what stops a model, because the moving clock would otherwise give every month a fresh
 * allowance.
 *
 * <p>The first run of a model also pays for the one-off embedding of the library (the index is
 * synced lazily), which shows in the cost of its first case.
 */
final class EvalHarness implements EvalEnvironment.Harness {

  /** Far above anything an eval can spend, so the ledger never refuses a call. */
  private static final String LEDGER_CAP_USD = "1000000";

  /** Reasons tokens add up, and a thinking model can be slow; longer than the product default. */
  private static final String CALL_TIMEOUT = "180s";

  private static final String INVALID_IDS_MARK = "not in the library";

  private final String model;
  private final String suffix;
  private final ConfigurableApplicationContext context;
  private final EvalClock clock;
  private final EvalHousehold data;
  private final EvalCase.Suite suite;
  private final AssistantService assistant;
  private final MealService meals;
  private final PlanningService planning;
  private final OccasionService occasions;
  private final RatingService ratings;
  private final long owner;
  private final List<Long> memberIds;
  private final long household;
  private final Map<String, Long> mealIds = new LinkedHashMap<>();
  private final Set<LocalDate> seededWeeks = new HashSet<>();
  private final Map<String, Checks.Answer> baselines = new HashMap<>();

  private EvalHarness(
      String model, ConfigurableApplicationContext context, EvalClock clock, EvalCase.Suite suite) {
    this.model = model;
    this.context = context;
    this.clock = clock;
    this.suite = suite;
    this.data = EvalHousehold.load();
    this.assistant = context.getBean(AssistantService.class);
    this.meals = context.getBean(MealService.class);
    this.planning = context.getBean(PlanningService.class);
    this.occasions = context.getBean(OccasionService.class);
    this.ratings = context.getBean(RatingService.class);
    JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
    HouseholdService households = context.getBean(HouseholdService.class);
    InviteService invites = context.getBean(InviteService.class);
    this.suffix = UUID.randomUUID().toString().substring(0, 8);
    List<Long> users = new ArrayList<>();
    for (EvalHousehold.Member member : data.members()) {
      users.add(HouseholdTestSupport.insertUser(jdbc, "eval-" + suffix + "-" + users.size()));
    }
    this.owner = users.getFirst();
    this.household = households.create(owner, data.name()).id();
    for (int i = 1; i < users.size(); i++) {
      String email = "eval-" + suffix + "-" + i + "@example.com";
      var invite = invites.create(household, owner, email, InviteExpiry.HOURS_24);
      invites.join(invite.code(), users.get(i), email);
    }
    this.memberIds = List.copyOf(users);
    for (EvalHousehold.Meal meal : data.meals()) {
      MealView made = create(meal.name(), meal.tags(), meal.ingredients(), meal.occasionTypes());
      mealIds.put(meal.key(), made.id());
      if (meal.archived()) {
        meals.archive(owner, household, made.id());
      }
    }
  }

  /**
   * Starts the application for one model and loads the household.
   *
   * @param model the chat model id, passed to Spring AI's Google GenAI settings
   * @param priceIn USD per million input tokens
   * @param priceOut USD per million output tokens
   * @param scripted {@code true} to use the scripted model of the {@code fake-ai} profile instead
   *     of Gemini; only the harness's own test does that
   * @return the running harness
   * @throws IllegalStateException if the application cannot start
   */
  static EvalHarness start(
      String model, BigDecimal priceIn, BigDecimal priceOut, boolean scripted) {
    EvalClock clock = new EvalClock();
    var postgres = AbstractPostgresIntegrationTest.POSTGRES;
    List<String> args = new ArrayList<>();
    args.add("--spring.datasource.url=" + postgres.getJdbcUrl());
    args.add("--spring.datasource.username=" + postgres.getUsername());
    args.add("--spring.datasource.password=" + postgres.getPassword());
    args.add("--mm.ai.enabled=true");
    args.add("--mm.ai.monthly-cap-usd=" + LEDGER_CAP_USD);
    args.add("--mm.ai.price-input-per-million-usd=" + priceIn.toPlainString());
    args.add("--mm.ai.price-output-per-million-usd=" + priceOut.toPlainString());
    args.add("--mm.ai.timeout=" + CALL_TIMEOUT);
    // The application needs its web layer to start (the session store is part of it), so a server
    // starts too, on a random port of this machine only. Nothing is ever sent to it.
    args.add("--server.port=0");
    args.add("--server.address=127.0.0.1");
    args.add("--spring.main.banner-mode=off");
    args.add("--logging.level.root=WARN");
    if (!scripted) {
      args.add("--spring.ai.google.genai.chat.model=" + model);
    }
    List<String> profiles = new ArrayList<>(List.of("dev", "postgres"));
    if (scripted) {
      profiles.add("fake-ai");
    }
    ApplicationContextInitializer<GenericApplicationContext> setup =
        ctx -> {
          ctx.getBeanFactory().registerSingleton("evalTestClassFilter", new TestClassFilter());
          ctx.registerBean(
              "evalClock", java.time.Clock.class, () -> clock, bd -> bd.setPrimary(true));
        };
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(MealMagicianApplication.class)
            .web(WebApplicationType.SERVLET)
            .profiles(profiles.toArray(String[]::new))
            .initializers(setup)
            .run(args.toArray(String[]::new));
    try {
      return new EvalHarness(scripted ? "scripted" : model, context, clock, EvalCase.load());
    } catch (RuntimeException e) {
      context.close();
      throw e;
    }
  }

  /**
   * Keeps the test classes out of the component scan. The application scans its whole package, and
   * the test sources hold {@code @TestConfiguration} classes that only a Spring test is meant to
   * pick up; Spring's own test support filters them, a plain {@code main} must do it itself.
   */
  private static final class TestClassFilter extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws IOException {
      return reader.getResource().getURL().toString().contains("/classes/java/test/");
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TestClassFilter;
    }

    @Override
    public int hashCode() {
      return TestClassFilter.class.hashCode();
    }
  }

  private MealView create(
      String name, List<String> tags, List<String> ingredients, List<OccasionType> types) {
    return meals.create(
        owner, household, new MealDetails(name, null, null, tags, types, ingredients));
  }

  @Override
  public RunResult run(EvalCase c, int run) {
    clock.setDate(c.date());
    ensureHistory(c.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    List<OccasionView> madeOccasions = new ArrayList<>();
    Map<String, Long> extraIds = new LinkedHashMap<>();
    try {
      for (EvalCase.CaseOccasion o : c.occasions()) {
        madeOccasions.add(
            occasions.create(
                owner, household, new OccasionDetails(o.name(), o.date(), o.type(), null)));
      }
      for (EvalCase.ExtraMeal extra : c.extraMeals()) {
        extraIds.put(
            extra.key(), create(extra.name(), extra.tags(), extra.ingredients(), List.of()).id());
      }
      return execute(c, run, madeOccasions, extraIds);
    } finally {
      madeOccasions.forEach(o -> occasions.delete(owner, household, o.id(), o.version()));
      extraIds.values().forEach(id -> meals.archive(owner, household, id));
    }
  }

  private RunResult execute(
      EvalCase c, int run, List<OccasionView> madeOccasions, Map<String, Long> extraIds) {
    MonthlyUsage before = assistant.usageThisMonth(owner, household);
    long started = System.nanoTime();
    Checks.Answer answer = null;
    String error = null;
    boolean invalidIds = false;
    try {
      answer = ask(c, madeOccasions);
    } catch (DraftGenerationException e) {
      error = "DraftGenerationException: " + e.getMessage();
      invalidIds = e.getMessage().contains(INVALID_IDS_MARK);
    } catch (RuntimeException e) {
      // The kind of failure only; the gateway's messages are prompt-free, others might not be.
      error = e.getClass().getSimpleName();
    }
    long millis = (System.nanoTime() - started) / 1_000_000;
    MonthlyUsage after = assistant.usageThisMonth(owner, household);
    List<Checks.Named> checks = List.of();
    if (answer != null) {
      Fixture fixture = fixture();
      Checks.Answer baseline =
          c.baseline() == null ? null : baselines.get(run + ":" + c.baseline());
      checks = Checks.evaluate(c, suite.reference(), fixture, extraIds, answer, baseline);
      invalidIds = !Checks.realIds(answer.mealIds(), fixture).pass();
      baselines.put(run + ":" + c.id(), answer);
    }
    return new RunResult(
        model,
        c.id(),
        run,
        checks,
        error,
        after.inputTokens() - before.inputTokens(),
        after.outputTokens() - before.outputTokens(),
        after.costMicroUsd() - before.costMicroUsd(),
        millis,
        invalidIds);
  }

  private Checks.Answer ask(EvalCase c, List<OccasionView> madeOccasions) {
    return switch (c.kind()) {
      case "week" -> {
        var draft = assistant.draftWeek(owner, household, c.asLocale(), c.date(), c.request());
        assistant.discard(owner, household, draft.id());
        yield Checks.Answer.of(draft);
      }
      case "slot" ->
          Checks.Answer.ofSlot(
              assistant.suggestSlot(
                  owner, household, c.asLocale(), c.slotDate(), SlotType.valueOf(c.slotType())));
      case "menu" ->
          Checks.Answer.of(
              assistant.suggestMenu(owner, household, c.asLocale(), madeOccasions.getFirst().id()));
      default ->
          Checks.Answer.of(assistant.chat(owner, household, c.asLocale(), List.of(), c.request()));
    };
  }

  private Fixture fixture() {
    List<Fixture.Meal> all =
        meals.search(owner, household, new MealSearch("", null, null, true)).stream()
            .map(m ->
                new Fixture.Meal(
                    m.id(),
                    m.name(),
                    m.tags(),
                    m.ingredients(),
                    Set.copyOf(m.occasionTypes()),
                    m.archived()))
            .toList();
    return new Fixture(all);
  }

  /** Plans and rates the three weeks before a case's week, once per week. */
  private void ensureHistory(LocalDate monday) {
    if (!seededWeeks.add(monday)) {
      return;
    }
    for (EvalHousehold.Planned planned : data.history()) {
      LocalDate day = monday.plusWeeks(planned.weekOffset()).plusDays(planned.day());
      clock.setDate(monday);
      SlotView slot =
          planning.add(
              owner,
              household,
              day,
              SlotType.valueOf(planned.slot()),
              mealIds.get(planned.meal()),
              null);
      for (EvalHousehold.Rating rating : planned.ratings()) {
        ratings.give(
            memberIds.get(rating.member()),
            household,
            slot.id(),
            new RatingInput(Thumb.valueOf(rating.thumb()), rating.stars(), rating.comment()));
      }
    }
  }

  /**
   * Removes the eval household and its people, then stops the context. The Postgres container is
   * shared with the integration tests of the same JVM, and some of them count rows of whole tables,
   * so the harness leaves nothing behind. Deleting the household cascades to its meals, plan,
   * ratings, drafts and usage.
   */
  @Override
  public void close() {
    try {
      JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
      jdbc.update("delete from household where id = ?", household);
      jdbc.update("delete from allowed_email where email like ?", "eval-" + suffix + "-%");
      for (long user : memberIds) {
        jdbc.update("delete from app_user where id = ?", user);
      }
    } finally {
      context.close();
    }
  }
}
