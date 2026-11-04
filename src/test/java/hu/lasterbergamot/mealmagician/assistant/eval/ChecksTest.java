package hu.lasterbergamot.mealmagician.assistant.eval;

import static hu.lasterbergamot.mealmagician.assistant.eval.EvalSamples.LIBRARY;
import static hu.lasterbergamot.mealmagician.assistant.eval.EvalSamples.MONDAY;
import static hu.lasterbergamot.mealmagician.assistant.eval.EvalSamples.draft;
import static hu.lasterbergamot.mealmagician.assistant.eval.EvalSamples.idea;
import static hu.lasterbergamot.mealmagician.assistant.eval.EvalSamples.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.assistant.DraftItemView;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.assistant.MenuSuggestions;
import hu.lasterbergamot.mealmagician.assistant.SlotSuggestion;
import hu.lasterbergamot.mealmagician.assistant.eval.EvalCase.CaseOccasion;
import hu.lasterbergamot.mealmagician.assistant.eval.EvalCase.CheckSpec;
import hu.lasterbergamot.mealmagician.assistant.eval.EvalCase.Meat;
import hu.lasterbergamot.mealmagician.assistant.eval.EvalCase.Reference;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Each check on a good and a bad sample answer, so the checker is proven without calling a model.
 */
class ChecksTest {

  private static final Meat MEAT = new Meat("húsos", List.of("marhahús", "csirke", "szalonna"));
  private static final Map<String, List<String>> STOP_WORDS =
      Map.of(
          "hu", List.of("az", "és", "egy", "hét", "csak", "van"),
          "en", List.of("the", "and", "with", "week", "only", "this"));

  @Test
  void realIdsPassesWhenEveryIdIsAnActiveMeal() {
    assertThat(Checks.realIds(List.of(1L, 2L), LIBRARY).pass()).isTrue();
  }

  @Test
  void realIdsFailsNamingAnIdThatDoesNotExist() {
    Checks.Result result = Checks.realIds(List.of(1L, 999L), LIBRARY);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("999");
  }

  @Test
  void realIdsFailsForAnArchivedMeal() {
    Checks.Result result = Checks.realIds(List.of(7L), LIBRARY);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("7");
  }

  @Test
  void noAdjacentRepeatFailsWhenTheSameMealIsOnMondayAndTuesday() {
    DraftView repeated = draft("x", item(0, SlotType.LUNCH, 1), item(1, SlotType.LUNCH, 1));

    Checks.Result result = Checks.noAdjacentRepeat(repeated, false);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("Gulyásleves");
  }

  @Test
  void noAdjacentRepeatFailsForLunchAndDinnerOfTheSameDay() {
    DraftView repeated = draft("x", item(2, SlotType.LUNCH, 3), item(2, SlotType.DINNER, 3));

    assertThat(Checks.noAdjacentRepeat(repeated, false).pass()).isFalse();
  }

  @Test
  void noAdjacentRepeatIsAllowedWhenTheCaseSaysSo() {
    DraftView repeated = draft("x", item(0, SlotType.LUNCH, 1), item(1, SlotType.LUNCH, 1));

    assertThat(Checks.noAdjacentRepeat(repeated, true).pass()).isTrue();
  }

  @Test
  void noAdjacentRepeatPassesWhenAMealComesBackAfterADay() {
    DraftView spaced =
        draft(
            "x",
            item(0, SlotType.LUNCH, 1),
            item(2, SlotType.LUNCH, 1),
            idea(1, SlotType.LUNCH, "Valami", List.of()));

    assertThat(Checks.noAdjacentRepeat(spaced, false).pass()).isTrue();
  }

  @Test
  void ideasCapPassesWithFiveIdeas() {
    assertThat(Checks.ideasCap(ideas(5), LIBRARY).pass()).isTrue();
  }

  @Test
  void ideasCapFailsWithSixIdeas() {
    Checks.Result result = Checks.ideasCap(ideas(6), LIBRARY);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("6 ideas");
  }

  @Test
  void ideasCapFailsForAnItemThatIsBothAMealAndAnIdea() {
    DraftItemView both =
        new DraftItemView(
            1, MONDAY, SlotType.LUNCH, 0, 1L, "Gulyásleves", "Gulyás", null, null, null);

    Checks.Result result = Checks.ideasCap(draft("x", both), LIBRARY);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("both");
  }

  @Test
  void ideasCapFailsForAnIdeaThatIsAlreadyInTheLibrary() {
    Checks.Result result =
        Checks.ideasCap(draft("x", idea(0, SlotType.DINNER, "gulyasleves", List.of())), LIBRARY);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("already in the library");
  }

  @Test
  void oneWeekPassesForItemsInsideTheRequestedWeek() {
    assertThat(
            Checks.oneWeek(
                    draft("x", item(0, SlotType.LUNCH, 1), item(6, SlotType.DINNER, 2)),
                    MONDAY.plusDays(3))
                .pass())
        .isTrue();
  }

  @Test
  void oneWeekFailsForAnItemInTheNextWeek() {
    DraftView late = draft("x", item(0, SlotType.LUNCH, 1), item(8, SlotType.LUNCH, 2));

    Checks.Result result = Checks.oneWeek(late, MONDAY);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains(MONDAY.plusDays(8).toString());
  }

  @Test
  void oneWeekFailsForAnEmptyDraftAndForAnotherWeek() {
    assertThat(Checks.oneWeek(draft("x"), MONDAY).pass()).isFalse();
    assertThat(Checks.oneWeek(draft("x", item(0, SlotType.LUNCH, 1)), MONDAY.plusWeeks(1)).pass())
        .isFalse();
  }

  @Test
  void languagePassesForAHungarianSummaryWhenHungarianWasAsked() {
    assertThat(
            Checks.language("Ez egy könnyű hét, és csak egyszerű ételek vannak.", "hu", STOP_WORDS)
                .pass())
        .isTrue();
  }

  @Test
  void languageFailsForAnEnglishSummaryWhenHungarianWasAsked() {
    Checks.Result result =
        Checks.language("This is the week with only simple food.", "hu", STOP_WORDS);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("hu");
  }

  @Test
  void languagePassesForAnEnglishSummaryWhenEnglishWasAsked() {
    assertThat(
            Checks.language("A simple week with the family favourites.", "en", STOP_WORDS).pass())
        .isTrue();
  }

  @Test
  void languageFailsForEmptyTextAndForTextWithNoKnownWords() {
    assertThat(Checks.language(null, "hu", STOP_WORDS).pass()).isFalse();
    assertThat(Checks.language("  ", "hu", STOP_WORDS).pass()).isFalse();
    assertThat(Checks.language("Bonjour tout le monde", "hu", STOP_WORDS).pass()).isFalse();
  }

  @Test
  void lessMeatFailsWhenFiveMeatMealsRemainOfSix() {
    Checks.Result result =
        Checks.lessMeat(EvalSamples.withMeat(5, 14), EvalSamples.withMeat(6, 14), LIBRARY, MEAT);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("36%").contains("43%");
  }

  @Test
  void lessMeatPassesWhenThreeMeatMealsRemainOfSix() {
    assertThat(
            Checks.lessMeat(EvalSamples.withMeat(3, 14), EvalSamples.withMeat(6, 14), LIBRARY, MEAT)
                .pass())
        .isTrue();
  }

  @Test
  void meatShareCountsTheTagTheIngredientsAndIdeas() {
    DraftView mixed =
        draft(
            "x",
            item(0, SlotType.LUNCH, 1),
            item(1, SlotType.LUNCH, 2),
            idea(2, SlotType.LUNCH, "Szalonnás tészta", List.of("gyors")),
            idea(3, SlotType.LUNCH, "Jó étel", List.of("húsos")));
    // The first is tagged and has meat; the third has no tag but its ingredient list is eggs only.
    assertThat(Checks.meatShare(mixed, LIBRARY, MEAT)).isEqualTo(0.5);
    assertThat(Checks.meatShare(draft("x"), LIBRARY, MEAT)).isZero();
  }

  @Test
  void lessMeatFailsWhenTheBaselineHasNoMeatAndTheDraftDoes() {
    assertThat(
            Checks.lessMeat(EvalSamples.withMeat(1, 14), EvalSamples.withMeat(0, 14), LIBRARY, MEAT)
                .pass())
        .isFalse();
  }

  @Test
  void occasionsPassWithAMatchingMealOnTheDate() {
    DraftView withCake = draft("x", item(5, SlotType.DINNER, 4));

    assertThat(Checks.occasions(withCake, LIBRARY, List.of(birthday())).pass()).isTrue();
  }

  @Test
  void occasionsPassWithAnIdeaOnTheDate() {
    DraftView withIdea = draft("x", idea(5, SlotType.DINNER, "Szülinapi sütemény", List.of()));

    assertThat(Checks.occasions(withIdea, LIBRARY, List.of(birthday())).pass()).isTrue();
  }

  @Test
  void occasionsFailWhenSaturdayHasNoMatchingMealAndNoIdea() {
    DraftView plain = draft("x", item(5, SlotType.DINNER, 1), item(4, SlotType.DINNER, 4));

    Checks.Result result = Checks.occasions(plain, LIBRARY, List.of(birthday()));

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("2026-11-14").contains("BIRTHDAY");
  }

  @Test
  void forbiddenIngredientsFailsForAMealWithTheWordAndPassesWithout() {
    Checks.Result bad =
        Checks.forbiddenIngredients(
            draft("x", item(0, SlotType.LUNCH, 5)), LIBRARY, List.of("gomba"));

    assertThat(bad.pass()).isFalse();
    assertThat(bad.message()).contains("Gombaleves");
    assertThat(
            Checks.forbiddenIngredients(
                    draft("x", item(0, SlotType.LUNCH, 1)), LIBRARY, List.of("gomba"))
                .pass())
        .isTrue();
  }

  @Test
  void forbiddenIngredientsAlsoLooksAtIdeas() {
    DraftView idea = draft("x", idea(0, SlotType.LUNCH, "Gombás rizottó", List.of()));

    assertThat(Checks.forbiddenIngredients(idea, LIBRARY, List.of("gomba")).pass()).isFalse();
  }

  @Test
  void usesIngredientPassesWhenOneMealUsesItAndFailsWhenNoneDoes() {
    assertThat(
            Checks.usesIngredient(draft("x", item(0, SlotType.LUNCH, 2)), LIBRARY, List.of("tojás"))
                .pass())
        .isTrue();
    assertThat(
            Checks.usesIngredient(draft("x", item(0, SlotType.LUNCH, 1)), LIBRARY, List.of("rizs"))
                .pass())
        .isFalse();
  }

  @Test
  void quickOnFailsForASlowMealOnThursdayAndPassesForQuickOnes() {
    DraftView slow = draft("x", item(3, SlotType.DINNER, 1));
    DraftView quick =
        draft(
            "x",
            item(3, SlotType.DINNER, 2),
            idea(3, SlotType.LUNCH, "Rántotta", List.of("gyors")));

    Checks.Result bad = Checks.quickOn(slow, LIBRARY, DayOfWeek.THURSDAY, "gyors");

    assertThat(bad.pass()).isFalse();
    assertThat(bad.message()).contains("Gulyásleves");
    assertThat(Checks.quickOn(quick, LIBRARY, DayOfWeek.THURSDAY, "gyors").pass()).isTrue();
  }

  @Test
  void quickOnIgnoresOtherDays() {
    assertThat(
            Checks.quickOn(
                    draft("x", item(0, SlotType.DINNER, 1)), LIBRARY, DayOfWeek.THURSDAY, "gyors")
                .pass())
        .isTrue();
  }

  @Test
  void maxUsesFailsWhenTheMealIsPlannedTooOften() {
    DraftView every =
        draft(
            "x",
            item(0, SlotType.LUNCH, 3),
            item(2, SlotType.LUNCH, 3),
            item(4, SlotType.LUNCH, 3));

    Checks.Result result = Checks.maxUses(every, 3, 1);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("3 times");
    assertThat(Checks.maxUses(draft("x", item(0, SlotType.LUNCH, 3)), 3, 1).pass()).isTrue();
  }

  @Test
  void seasonalFailsWhenOutOfSeasonMealsDominate() {
    DraftView summerInWinter =
        draft(
            "x",
            item(0, SlotType.LUNCH, 6),
            item(2, SlotType.LUNCH, 6),
            item(4, SlotType.LUNCH, 1));

    Checks.Result result = Checks.seasonal(summerInWinter, LIBRARY, List.of("görögdinnye"), 1);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("2 of 3").contains("Görögdinnyés saláta");
  }

  @Test
  void seasonalPassesWhenOnlyAFewItemsAreOutOfSeason() {
    DraftView mostlyFine =
        draft(
            "x",
            item(0, SlotType.LUNCH, 6),
            item(1, SlotType.LUNCH, 1),
            item(2, SlotType.LUNCH, 2),
            item(3, SlotType.LUNCH, 3));

    assertThat(Checks.seasonal(mostlyFine, LIBRARY, List.of("görögdinnye"), 1).pass()).isTrue();
  }

  @Test
  void seasonalFailsWithoutATableForTheMonth() {
    Checks.Result result =
        Checks.seasonal(draft("x", item(0, SlotType.LUNCH, 1)), LIBRARY, List.of(), 3);

    assertThat(result.pass()).isFalse();
    assertThat(result.message()).contains("month 3");
  }

  @Test
  void staysOnTopicPassesForAOneSentenceRefusalAboutFood() {
    assertThat(
            Checks.staysOnTopic(
                    "Csak az ételekkel és az étkezési tervvel kapcsolatban tudok segíteni.")
                .pass())
        .isTrue();
  }

  @Test
  void staysOnTopicFailsForAPoem() {
    String poem = "A tenger kék,\na hullám zúg,\na sirály száll,\na part hideg,\na nap lenyugszik.";

    assertThat(Checks.staysOnTopic(poem).pass()).isFalse();
  }

  @Test
  void staysOnTopicFailsForAShortAnswerThatNeverMentionsFood() {
    assertThat(Checks.staysOnTopic("Ez egy szép vers lesz a tengerről.").pass()).isFalse();
    assertThat(Checks.staysOnTopic("").pass()).isFalse();
    assertThat(Checks.staysOnTopic("étel ".repeat(100)).pass()).isFalse();
  }

  @Test
  void slotCapFailsWithFourPicks() {
    List<SlotSuggestion> four = new ArrayList<>();
    for (long id = 1; id <= 4; id++) {
      four.add(new SlotSuggestion(id, "Meal " + id, null));
    }

    assertThat(Checks.slotCap(four).pass()).isFalse();
    assertThat(Checks.slotCap(four.subList(0, 3)).pass()).isTrue();
  }

  @Test
  void menuCapsFailForTooManyMealsOrIdeas() {
    List<MenuSuggestions.MenuMeal> meals = new ArrayList<>();
    for (long id = 1; id <= 7; id++) {
      meals.add(new MenuSuggestions.MenuMeal(id, "Meal " + id));
    }
    MenuSuggestions.MenuIdea idea =
        new MenuSuggestions.MenuIdea("Ötlet", "Jó", List.of(), List.of());

    assertThat(Checks.menuCaps(new MenuSuggestions(meals, List.of())).pass()).isFalse();
    assertThat(
            Checks.menuCaps(
                    new MenuSuggestions(meals.subList(0, 6), List.of(idea, idea, idea, idea)))
                .pass())
        .isFalse();
    assertThat(
            Checks.menuCaps(new MenuSuggestions(meals.subList(0, 6), List.of(idea, idea, idea)))
                .pass())
        .isTrue();
  }

  @Test
  void foldRemovesAccentsAndCase() {
    assertThat(Checks.fold("Gombás TŰZ")).isEqualTo("gombas tuz");
    assertThat(Checks.fold(null)).isEmpty();
  }

  @Test
  void answersListTheirMealIds() {
    assertThat(
            Checks.Answer.of(
                    draft(
                        "x",
                        item(0, SlotType.LUNCH, 1),
                        idea(1, SlotType.LUNCH, "Ötlet", List.of())))
                .mealIds())
        .containsExactly(1L);
    assertThat(Checks.Answer.ofSlot(List.of(new SlotSuggestion(2, "x", null))).mealIds())
        .containsExactly(2L);
    assertThat(
            Checks.Answer.of(
                    new MenuSuggestions(List.of(new MenuSuggestions.MenuMeal(3, "x")), List.of()))
                .mealIds())
        .containsExactly(3L);
    assertThat(Checks.Answer.of("szia").mealIds()).isEmpty();
  }

  @Test
  void evaluateAppliesEachListedCheckAndFailsAnUnknownOne() {
    Reference reference = new Reference(MEAT, Map.of("11", List.of("görögdinnye")), STOP_WORDS);
    EvalCase week =
        caseOf(
            "week",
            spec("realIds"),
            spec("noAdjacentRepeat"),
            spec("ideasCap"),
            spec("oneWeek"),
            spec("language"),
            spec("lessMeat"),
            spec("occasion"),
            new CheckSpec("forbiddenIngredients", null, null, List.of("gomba"), null, null),
            new CheckSpec("usesIngredient", null, null, List.of("tojás"), null, null),
            new CheckSpec("quickOn", "THURSDAY", "gyors", null, null, null),
            new CheckSpec("maxUses", null, null, null, "extra", 1),
            spec("seasonal"));
    DraftView good =
        draft(
            "Egy hét, és csak jó ételek.", item(0, SlotType.LUNCH, 2), item(2, SlotType.DINNER, 4));
    DraftView base = EvalSamples.withMeat(6, 14);

    List<Checks.Named> results =
        Checks.evaluate(
            week,
            reference,
            LIBRARY,
            Map.of("extra", 3L),
            Checks.Answer.of(good),
            Checks.Answer.of(base));

    assertThat(results)
        .hasSize(12)
        .allSatisfy(named -> assertThat(named.result().pass()).as(named.check()).isTrue());
    assertThat(
            Checks.evaluate(week, reference, LIBRARY, Map.of(), Checks.Answer.of(good), null)
                .get(5)
                .result()
                .pass())
        .isFalse();
    assertThatThrownBy(() ->
            Checks.evaluate(
                caseOf("week", spec("nonsense")),
                reference,
                LIBRARY,
                Map.of(),
                Checks.Answer.of(good),
                null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonsense");
  }

  @Test
  void evaluateAppliesTheChecksOfTheOtherKinds() {
    Reference reference = new Reference(MEAT, Map.of(), STOP_WORDS);

    assertThat(
            Checks.evaluate(
                caseOf("slot", spec("realIds"), spec("slotCap")),
                reference,
                LIBRARY,
                Map.of(),
                Checks.Answer.ofSlot(List.of(new SlotSuggestion(1, "x", null))),
                null))
        .allSatisfy(n -> assertThat(n.result().pass()).isTrue());
    assertThat(
            Checks.evaluate(
                caseOf("menu", spec("realIds"), spec("menuCaps")),
                reference,
                LIBRARY,
                Map.of(),
                Checks.Answer.of(
                    new MenuSuggestions(
                        List.of(new MenuSuggestions.MenuMeal(999, "x")), List.of())),
                null))
        .extracting(n -> n.result().pass())
        .containsExactly(false, true);
    assertThat(
            Checks.evaluate(
                caseOf("chat", spec("language"), spec("staysOnTopic")),
                reference,
                LIBRARY,
                Map.of(),
                Checks.Answer.of("Csak az étel van és csak ennyit tudok."),
                null))
        .allSatisfy(n -> assertThat(n.result().pass()).isTrue());
  }

  @Test
  void everyKindOnlyAllowsItsOwnChecks() {
    assertThat(Checks.allowedFor("chat")).containsExactlyInAnyOrder("language", "staysOnTopic");
    assertThat(Checks.allowedFor("nothing")).isEmpty();
    assertThat(Checks.NAMES).contains("realIds", "staysOnTopic", "seasonal");
  }

  private static CheckSpec spec(String name) {
    return new CheckSpec(name, null, null, null, null, null);
  }

  private static EvalCase caseOf(String kind, CheckSpec... checks) {
    return new EvalCase(
        "sample",
        kind,
        "hu",
        MONDAY,
        "kérés",
        "base",
        false,
        List.of(new CaseOccasion("Szülinap", MONDAY.plusDays(2), OccasionType.BIRTHDAY)),
        List.of(),
        null,
        null,
        List.of(checks));
  }

  private static CaseOccasion birthday() {
    return new CaseOccasion("Anna szülinapja", LocalDate.of(2026, 11, 14), OccasionType.BIRTHDAY);
  }

  private static DraftView ideas(int count) {
    List<DraftItemView> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(idea(i % 7, SlotType.DINNER, "Ötlet " + i, List.of()));
    }
    return draft("x", items.toArray(DraftItemView[]::new));
  }
}
