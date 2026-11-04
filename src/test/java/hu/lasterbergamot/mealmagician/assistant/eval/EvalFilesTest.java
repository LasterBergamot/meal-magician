package hu.lasterbergamot.mealmagician.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The case file and the household file are well formed and fit each other. */
class EvalFilesTest {

  private final EvalCase.Suite suite = EvalCase.load();
  private final EvalHousehold household = EvalHousehold.load();

  @Test
  void thereAreBetweenTwelveAndSixteenCasesWithUniqueIds() {
    assertThat(suite.cases().size()).isBetween(12, 16);
    assertThat(suite.cases().stream().map(EvalCase::id).distinct().count())
        .isEqualTo(suite.cases().size());
  }

  @Test
  void theCasesCoverTheScenariosOfTheIssue() {
    assertThat(suite.cases().stream().map(EvalCase::id))
        .contains(
            "plain-week",
            "less-meat",
            "busy-thursday",
            "birthday-saturday",
            "christmas-week",
            "leftover-rice",
            "no-mushrooms",
            "english-request",
            "injection-name",
            "three-weeks",
            "january-week",
            "july-week",
            "chat-off-topic");
    assertThat(suite.get("english-request").locale()).isEqualTo("en");
    assertThat(suite.get("january-week").date().getMonthValue()).isEqualTo(1);
    assertThat(suite.get("july-week").date().getMonthValue()).isEqualTo(7);
    assertThat(suite.get("christmas-week").allowAdjacentRepeat()).isTrue();
    assertThat(suite.get("less-meat").baseline()).isEqualTo("plain-week");
  }

  @Test
  void everyCaseHasChecksOfItsKindAndAWeekCaseAWeekRequest() {
    for (EvalCase c : suite.cases()) {
      assertThat(c.checks()).as(c.id()).isNotEmpty();
      assertThat(c.date().getDayOfWeek()).as(c.id()).isEqualTo(DayOfWeek.MONDAY);
      if (c.kind().equals("week") || c.kind().equals("chat")) {
        assertThat(c.request()).as(c.id()).isNotBlank();
      }
    }
  }

  @Test
  void theSeasonalCasesHaveATableForTheirMonth() {
    for (String id : List.of("january-week", "july-week")) {
      String month = String.valueOf(suite.get(id).date().getMonthValue());
      assertThat(suite.reference().outOfSeason().get(month)).as(id).isNotEmpty();
    }
  }

  @Test
  void theHouseholdIsAboutFortyMealsAndThreeMembersAndSomeAreArchived() {
    assertThat(household.meals().size()).isBetween(38, 45);
    assertThat(household.members()).hasSize(3);
    assertThat(household.meals().stream().filter(EvalHousehold.Meal::archived).count())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void theLibraryHasWhatTheChecksLookFor() {
    Set<String> tags = new HashSet<>();
    Set<OccasionType> types = new HashSet<>();
    Set<String> ingredients = new HashSet<>();
    household
        .meals()
        .forEach(m -> {
          tags.addAll(m.tags());
          types.addAll(m.occasionTypes());
          ingredients.addAll(m.ingredients());
        });

    assertThat(tags).contains(suite.reference().meat().tag(), "gyors");
    assertThat(types).contains(OccasionType.BIRTHDAY, OccasionType.HOLIDAY);
    assertThat(ingredients).contains("rizs", "gomba");
  }

  @Test
  void theHistoryIsRatedByAllThreeMembersWithThumbsStarsAndComments() {
    var ratings = household.history().stream().flatMap(p -> p.ratings().stream()).toList();

    assertThat(ratings.stream().map(EvalHousehold.Rating::member).distinct()).hasSize(3);
    assertThat(ratings.stream().map(EvalHousehold.Rating::thumb).distinct())
        .containsExactlyInAnyOrder("UP", "DOWN");
    assertThat(ratings.stream().anyMatch(r -> r.stars() != null)).isTrue();
    assertThat(ratings.stream().anyMatch(r -> r.comment() != null)).isTrue();
    assertThat(household.history().stream().map(EvalHousehold.Planned::weekOffset).distinct())
        .containsExactlyInAnyOrder(-1, -2, -3);
  }

  @Test
  void aBrokenCaseFileIsRefusedWithAMessage() {
    assertThatThrownBy(() -> EvalCase.parse("{not json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not valid");
    assertThatThrownBy(() ->
            EvalCase.parse(
                withCases(
                    one("a", "week", "realIds", null) + "," + one("a", "week", "realIds", null))))
        .hasMessageContaining("duplicate");
    assertThatThrownBy(() -> EvalCase.parse(withCases(one("a", "poem", "realIds", null))))
        .hasMessageContaining("unknown kind");
    assertThatThrownBy(() -> EvalCase.parse(withCases(one("a", "chat", "realIds", null))))
        .hasMessageContaining("cannot use the check realIds");
    assertThatThrownBy(() -> EvalCase.parse(withCases(one("a", "week", "realIds", "\"later\""))))
        .hasMessageContaining("needs its baseline later");
    assertThat(EvalCase.parse(withCases(one("a", "week", "realIds", null))).cases()).hasSize(1);
  }

  @Test
  void aCaseFileWithoutListsGetsEmptyOnes() {
    EvalCase c = EvalCase.parse(withCases(one("a", "week", "realIds", null))).get("a");

    assertThat(c.occasions()).isEmpty();
    assertThat(c.extraMeals()).isEmpty();
    assertThat(c.asLocale().getLanguage()).isEqualTo("hu");
    assertThatThrownBy(() -> suite.get("zzz")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBrokenHouseholdFileIsRefusedWithAMessage() {
    String meal = "{\"key\":\"a\",\"name\":\"A\"}";
    assertThatThrownBy(() -> EvalHousehold.parse("{")).hasMessageContaining("not valid");
    assertThatThrownBy(() -> EvalHousehold.parse(house(meal + "," + meal, "")))
        .hasMessageContaining("duplicate meal key a");
    assertThatThrownBy(() ->
            EvalHousehold.parse(
                house(meal, "{\"weekOffset\":-1,\"day\":0,\"slot\":\"LUNCH\",\"meal\":\"b\"}")))
        .hasMessageContaining("unknown meal b");
    assertThatThrownBy(() ->
            EvalHousehold.parse(
                house(
                    meal,
                    "{\"weekOffset\":-1,\"day\":0,\"slot\":\"LUNCH\",\"meal\":\"a\",\"ratings\":[{\"member\":4,\"thumb\":\"UP\"}]}")))
        .hasMessageContaining("unknown member 4");
    assertThat(EvalHousehold.parse(house(meal, "")).meals().getFirst().tags()).isEmpty();
  }

  @Test
  void theFixtureFindsActiveMealsOnly() {
    Fixture fixture = EvalSamples.LIBRARY;

    assertThat(fixture.isActive(1)).isTrue();
    assertThat(fixture.isActive(7)).isFalse();
    assertThat(fixture.isActive(99)).isFalse();
  }

  @Test
  void theClockShowsTheDayACaseSets() {
    EvalClock clock = new EvalClock();

    clock.setDate(LocalDate.of(2027, 1, 11));

    assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2027, 1, 11));
    assertThat(clock.getZone().getId()).isEqualTo("Europe/Budapest");
    assertThat(clock.withZone(java.time.ZoneOffset.UTC).instant()).isEqualTo(clock.instant());
  }

  private static String one(String id, String kind, String check, String baseline) {
    return "{\"id\":\""
        + id
        + "\",\"kind\":\""
        + kind
        + "\",\"locale\":\"hu\",\"date\":\"2026-11-09\",\"baseline\":"
        + baseline
        + ",\"checks\":[{\"check\":\""
        + check
        + "\"}]}";
  }

  private static String withCases(String cases) {
    return "{\"reference\":{\"meat\":{\"tag\":\"húsos\",\"words\":[]},\"outOfSeason\":{},\"stopWords\":{}},\"cases\":["
        + cases
        + "]}";
  }

  private static String house(String meals, String history) {
    return "{\"name\":\"H\",\"members\":[{\"name\":\"A\"}],\"meals\":["
        + meals
        + "],\"history\":["
        + history
        + "]}";
  }
}
