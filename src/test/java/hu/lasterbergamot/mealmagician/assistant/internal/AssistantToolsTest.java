package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.lasterbergamot.mealmagician.assistant.internal.AssistantTools.CurrentDate;
import hu.lasterbergamot.mealmagician.assistant.internal.AssistantTools.MemberPreferences;
import hu.lasterbergamot.mealmagician.catalog.MealDetails;
import hu.lasterbergamot.mealmagician.catalog.MealService;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.feedback.RatingInput;
import hu.lasterbergamot.mealmagician.feedback.RatingService;
import hu.lasterbergamot.mealmagician.feedback.Thumb;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.household.MemberService;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionService;
import hu.lasterbergamot.mealmagician.planning.PlanningService;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.planning.SlotView;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The model's tools against the real services on the dev profile's H2 (the embedding index is a
 * mock: it needs Postgres and a model). Each test rolls back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AssistantToolsTest {

  @Autowired private PlanningService planning;
  @Autowired private MealService meals;
  @Autowired private RatingService ratings;
  @Autowired private OccasionService occasions;
  @Autowired private HouseholdService households;
  @Autowired private MemberService members;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private final EmbeddingIndex index = mock(EmbeddingIndex.class);

  private LocalDate today;
  private LocalDate monday;
  private long anna;
  private long zsuzsa;
  private long bela;
  private long home;
  private long other;
  private long stew;
  private long soup;

  @BeforeEach
  void setUp() {
    today = LocalDate.now(clock);
    monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    anna = HouseholdTestSupport.insertUser(jdbc, "tl-anna");
    zsuzsa = HouseholdTestSupport.insertUser(jdbc, "Zsuzsa");
    bela = HouseholdTestSupport.insertUser(jdbc, "tl-bela");
    home = households.create(anna, "Home").id();
    other = households.create(bela, "Other").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at)"
            + " values (?, ?, 'MEMBER', now())",
        home,
        zsuzsa);
    stew =
        meals
            .create(
                anna,
                home,
                new MealDetails("Stew", "slow", null, List.of("winter"), null, List.of("beef")))
            .id();
    soup = meals.quickAdd(anna, home, "Soup").id();
  }

  private AssistantTools tools(long user, long household) {
    return new AssistantTools(
        user,
        household,
        Pseudonyms.of(members.list(household, user)),
        planning,
        meals,
        ratings,
        occasions,
        index,
        clock);
  }

  private SlotView plan(LocalDate date, SlotType type, long meal) {
    return planning.add(anna, home, date, type, meal, null);
  }

  // ---- the shape of the tool set ----

  @Test
  void exactlyTheSixReadOnlyToolsExistAndNoneTakesAHouseholdOrUserId() {
    List<Method> toolMethods =
        Arrays.stream(AssistantTools.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(Tool.class))
            .toList();

    assertThat(toolMethods)
        .extracting(Method::getName)
        .containsExactlyInAnyOrder(
            "recentWeeks",
            "searchMeals",
            "mealDetails",
            "householdPreferences",
            "upcomingOccasions",
            "currentDate");
    for (Method method : toolMethods) {
      for (Parameter parameter : method.getParameters()) {
        assertThat(parameter.getName().toLowerCase())
            .as("%s parameter %s", method.getName(), parameter.getName())
            .doesNotContain("household")
            .doesNotContain("user")
            .doesNotContain("member")
            .doesNotStartWith("arg");
      }
    }
  }

  @Test
  void theSchemaTheModelSeesHasNoHouseholdOrUserAndNoToolWrites() {
    ToolCallback[] callbacks = ToolCallbacks.from(tools(anna, home));

    assertThat(callbacks).hasSize(6);
    for (ToolCallback callback : callbacks) {
      String schema = callback.getToolDefinition().inputSchema().toLowerCase();
      assertThat(schema).doesNotContain("household").doesNotContain("userid");
      assertThat(callback.getToolDefinition().name())
          .doesNotStartWith("add")
          .doesNotStartWith("create")
          .doesNotStartWith("delete")
          .doesNotStartWith("remove")
          .doesNotStartWith("save")
          .doesNotStartWith("apply")
          .doesNotStartWith("accept")
          .doesNotStartWith("update");
    }
  }

  // ---- recentWeeks ----

  @Test
  void recentWeeksListsPlannedMealsNewestWeekFirstAndSkipsEmptyWeeks() {
    plan(monday, SlotType.LUNCH, stew);
    plan(monday.minusWeeks(2), SlotType.DINNER, soup);

    var weeks = tools(anna, home).recentWeeks(3);

    assertThat(weeks)
        .extracting(AssistantTools.PlannedWeek::weekStart)
        .containsExactly(monday.toString(), monday.minusWeeks(2).toString());
    assertThat(weeks.getFirst().meals())
        .singleElement()
        .satisfies(m -> {
          assertThat(m.name()).isEqualTo("Stew");
          assertThat(m.slot()).isEqualTo("LUNCH");
          assertThat(m.date()).isEqualTo(monday.toString());
          assertThat(m.mealId()).isEqualTo(stew);
        });
  }

  @Test
  void recentWeeksClampsTheNumberOfWeeks() {
    plan(monday, SlotType.LUNCH, stew);
    plan(monday.minusWeeks(1), SlotType.LUNCH, stew);
    plan(monday.minusWeeks(20), SlotType.LUNCH, stew);

    assertThat(tools(anna, home).recentWeeks(0)).hasSize(1);
    assertThat(tools(anna, home).recentWeeks(-3)).hasSize(1);
    assertThat(tools(anna, home).recentWeeks(1000)).hasSize(2);
  }

  // ---- searchMeals ----

  @Test
  void searchMealsSyncsTheIndexOncePerRequestAndReturnsOnlyIdAndName() {
    when(index.search(eq(anna), eq(home), anyString(), anyInt())).thenReturn(List.of(stew, 999L));
    var tools = tools(anna, home);

    var first = tools.searchMeals("hearty", 5);
    tools.searchMeals("light", 5);

    assertThat(first)
        .singleElement()
        .satisfies(m -> {
          assertThat(m.id()).isEqualTo(stew);
          assertThat(m.name()).isEqualTo("Stew");
        });
    assertThat(AssistantTools.MealName.class.getRecordComponents())
        .extracting(c -> c.getName())
        .containsExactly("id", "name");
    verify(index, times(1)).sync(anna, home);
  }

  @Test
  void theFifthSearchInOneRequestReturnsNothingAndDoesNotTouchTheIndex() {
    when(index.search(eq(anna), eq(home), anyString(), anyInt())).thenReturn(List.of(stew));
    var tools = tools(anna, home);

    for (int i = 0; i < AssistantTools.MAX_SEARCHES; i++) {
      assertThat(tools.searchMeals("q" + i, 5)).hasSize(1);
    }
    assertThat(tools.searchMeals("one more", 5)).isEmpty();
    assertThat(tools.searchMeals("and another", 5)).isEmpty();

    assertThat(AssistantTools.MAX_SEARCHES).isEqualTo(4);
    verify(index, times(AssistantTools.MAX_SEARCHES))
        .search(eq(anna), eq(home), anyString(), anyInt());
    verify(index, never()).search(anna, home, "one more", 5);
  }

  @Test
  void aNewToolsObjectStartsCountingSearchesAgain() {
    when(index.search(eq(anna), eq(home), anyString(), anyInt())).thenReturn(List.of(stew));
    var spent = tools(anna, home);
    for (int i = 0; i < AssistantTools.MAX_SEARCHES; i++) {
      spent.searchMeals("q", 5);
    }

    assertThat(spent.searchMeals("q", 5)).isEmpty();
    assertThat(tools(anna, home).searchMeals("q", 5)).hasSize(1);
  }

  @Test
  void theSearchToolTextStatesTheLimitAndPointsToMealDetails() throws Exception {
    String description =
        AssistantTools.class
            .getMethod("searchMeals", String.class, int.class)
            .getAnnotation(Tool.class)
            .description();

    assertThat(description).contains("4 times").contains("mealDetails").contains("empty list");
  }

  @Test
  void aBlankQueryDoesNotUseUpASearch() {
    when(index.search(eq(anna), eq(home), anyString(), anyInt())).thenReturn(List.of(stew));
    var tools = tools(anna, home);

    for (int i = 0; i < 10; i++) {
      tools.searchMeals(" ", 5);
    }

    assertThat(tools.searchMeals("q", 5)).hasSize(1);
  }

  @Test
  void mealDetailsStillCarriesTagsAndIngredients() {
    assertThat(tools(anna, home).mealDetails(List.of(stew)))
        .singleElement()
        .satisfies(m -> {
          assertThat(m.tags()).containsExactly("winter");
          assertThat(m.ingredients()).containsExactly("beef");
        });
  }

  @Test
  void searchMealsClampsTheLimit() {
    when(index.search(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(List.of());
    var tools = tools(anna, home);

    tools.searchMeals("x", 100);
    tools.searchMeals("x", 0);

    verify(index).search(anna, home, "x", AssistantTools.MAX_MEALS);
    verify(index).search(anna, home, "x", 1);
  }

  @Test
  void aBlankQueryMakesNeitherASyncNorASearch() {
    var tools = tools(anna, home);

    assertThat(tools.searchMeals(null, 5)).isEmpty();
    assertThat(tools.searchMeals("   ", 5)).isEmpty();

    verify(index, never()).sync(anyLong(), anyLong());
    verify(index, never()).search(anyLong(), anyLong(), anyString(), anyInt());
  }

  // ---- mealDetails ----

  @Test
  void mealDetailsSkipsUnknownDuplicateAndNullIdsAndReadsAtMostFifteen() {
    List<Long> ids = new ArrayList<>();
    ids.add(null);
    ids.add(stew);
    ids.add(stew);
    ids.add(123456789L);
    for (int i = 0; i < 20; i++) {
      ids.add(meals.quickAdd(anna, home, "Extra " + i).id());
    }
    meals.archive(anna, home, soup);
    ids.add(1, soup);

    var details = tools(anna, home).mealDetails(ids);

    assertThat(details).hasSize(AssistantTools.MAX_MEALS);
    assertThat(details.get(0).name()).isEqualTo("Soup");
    assertThat(details.get(0).archived()).isTrue();
    assertThat(details.get(1).notes()).isEqualTo("slow");
    assertThat(details.get(1).tags()).containsExactly("winter");
    assertThat(details.get(1).occasionTypes()).isEmpty();
    assertThat(tools(anna, home).mealDetails(null)).isEmpty();
  }

  @Test
  void mealDetailsShowsOccasionTypes() {
    meals.edit(
        anna,
        home,
        soup,
        new MealDetails("Soup", null, null, List.of(), List.of(OccasionType.BIRTHDAY), List.of()));

    assertThat(tools(anna, home).mealDetails(List.of(soup)))
        .singleElement()
        .extracting(AssistantTools.MealDetail::occasionTypes)
        .isEqualTo(List.of("BIRTHDAY"));
  }

  // ---- householdPreferences ----

  @Test
  void preferencesGroupByMemberNumberAndNeverShowADisplayName() {
    SlotView stewSlot = plan(today.minusDays(3), SlotType.DINNER, stew);
    SlotView soupSlot = plan(today.minusDays(2), SlotType.LUNCH, soup);
    SlotView stewAgain = plan(today.minusDays(1), SlotType.DINNER, stew);
    ratings.give(anna, home, stewSlot.id(), new RatingInput(Thumb.UP, 5, "lovely"));
    ratings.give(anna, home, stewAgain.id(), new RatingInput(Thumb.UP, null, "  "));
    ratings.give(anna, home, soupSlot.id(), new RatingInput(Thumb.UP, null, null));
    ratings.give(zsuzsa, home, stewSlot.id(), new RatingInput(Thumb.DOWN, 2, "too salty"));

    var prefs = tools(anna, home).householdPreferences();

    assertThat(prefs).extracting(MemberPreferences::member).containsExactly("member 1", "member 2");
    MemberPreferences first = prefs.get(0);
    assertThat(first.liked()).containsExactlyInAnyOrder("Stew", "Soup");
    assertThat(first.disliked()).isEmpty();
    assertThat(first.comments())
        .singleElement()
        .extracting(AssistantTools.RatedComment::comment)
        .isEqualTo("lovely");
    MemberPreferences second = prefs.get(1);
    assertThat(second.disliked()).containsExactly("Stew");
    assertThat(second.comments())
        .singleElement()
        .satisfies(c -> {
          assertThat(c.comment()).isEqualTo("too salty");
          assertThat(c.thumb()).isEqualTo("DOWN");
          assertThat(c.meal()).isEqualTo("Stew");
        });
    assertThat(prefs.toString()).doesNotContain("Zsuzsa").doesNotContain("example.com");
  }

  @Test
  void aFormerMembersRatingsAreNotListed() {
    SlotView slot = plan(today.minusDays(1), SlotType.DINNER, stew);
    ratings.give(zsuzsa, home, slot.id(), new RatingInput(Thumb.UP, null, "gone"));
    jdbc.update("delete from membership where household_id = ? and user_id = ?", home, zsuzsa);

    assertThat(tools(anna, home).householdPreferences()).isEmpty();
  }

  // ---- upcomingOccasions and currentDate ----

  @Test
  void upcomingOccasionsListsTheNextDaysAndClampsTheRange() {
    var soon =
        occasions.create(
            anna,
            home,
            new OccasionDetails("Name day", today.plusDays(3), OccasionType.PARTY, null));
    occasions.addItem(anna, home, soon.id(), stew);
    occasions.create(anna, home, new OccasionDetails("Today", today, OccasionType.OTHER, null));
    occasions.create(
        anna, home, new OccasionDetails("Far", today.plusDays(200), OccasionType.HOLIDAY, null));

    var tools = tools(anna, home);

    assertThat(tools.upcomingOccasions(0))
        .extracting(AssistantTools.UpcomingOccasion::name)
        .containsExactly("Today");
    assertThat(tools.upcomingOccasions(-4)).hasSize(1);
    var week = tools.upcomingOccasions(7);
    assertThat(week)
        .extracting(AssistantTools.UpcomingOccasion::name)
        .containsExactly("Today", "Name day");
    assertThat(week.get(0).hasMenu()).isFalse();
    assertThat(week.get(1).hasMenu()).isTrue();
    assertThat(week.get(1).type()).isEqualTo("PARTY");
    assertThat(week.get(1).date()).isEqualTo(today.plusDays(3).toString());
    assertThat(tools.upcomingOccasions(10_000)).hasSize(2);
  }

  @Test
  void currentDateGivesTodayTheWeekdayAndTheLocation() {
    CurrentDate date = tools(anna, home).currentDate();

    assertThat(date.date()).isEqualTo(today.toString());
    assertThat(date.dayOfWeek()).isEqualTo(today.getDayOfWeek().name());
    assertThat(date.location()).isEqualTo("Hungary, Central Europe");
  }

  // ---- tenant safety ----

  @Test
  void toolsBoundToOneHouseholdNeverReturnTheDataOfAnother() {
    // Everything lives in the "other" household; the tools are bound to the empty home of Anna's
    // neighbour. Build a third household for the tools so that home's own data does not count.
    long carol = HouseholdTestSupport.insertUser(jdbc, "tl-carol");
    long empty = households.create(carol, "Empty").id();
    long theirMeal = meals.quickAdd(bela, other, "Their secret").id();
    SlotView theirSlot =
        planning.add(bela, other, today.minusDays(1), SlotType.DINNER, theirMeal, null);
    ratings.give(bela, other, theirSlot.id(), new RatingInput(Thumb.UP, null, "their comment"));
    planning.add(bela, other, monday, SlotType.LUNCH, theirMeal, null);
    occasions.create(
        bela,
        other,
        new OccasionDetails("Their party", today.plusDays(1), OccasionType.PARTY, null));
    when(index.search(eq(carol), eq(empty), anyString(), anyInt())).thenReturn(List.of(theirMeal));
    var tools = tools(carol, empty);

    assertThat(tools.recentWeeks(8)).isEmpty();
    assertThat(tools.searchMeals("secret", 15)).isEmpty();
    assertThat(tools.mealDetails(List.of(theirMeal))).isEmpty();
    assertThat(tools.householdPreferences()).isEmpty();
    assertThat(tools.upcomingOccasions(90)).isEmpty();
    Set<String> toolNames =
        Arrays.stream(AssistantTools.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(Tool.class))
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertThat(toolNames).hasSize(6);
  }
}
