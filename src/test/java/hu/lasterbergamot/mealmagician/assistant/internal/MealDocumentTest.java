package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.MealView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The meal document is a pure function: stable text, the right fields, capped length. */
class MealDocumentTest {

  static MealView meal(String name, String notes, List<String> tags, List<String> ingredients) {
    return new MealView(
        1L,
        name,
        notes,
        "https://example.com/recipe",
        tags,
        List.of(OccasionType.BIRTHDAY, OccasionType.HOLIDAY),
        false,
        3L,
        ingredients);
  }

  @Test
  void listsEveryFieldOnItsOwnLine() {
    var text =
        MealDocument.of(
            meal(
                "Lentil soup",
                "Better the next day",
                List.of("vegan", "soup"),
                List.of("lencse", "répa")));

    assertThat(text)
        .isEqualTo(
            """
            Name: Lentil soup
            Tags: vegan, soup
            Ingredients: lencse, répa
            Occasions: BIRTHDAY, HOLIDAY
            Notes: Better the next day\
            """);
  }

  @Test
  void leavesOutEmptyLinesAndTheRecipeLink() {
    var bare =
        new MealView(
            2L, "Toast", null, "https://x.example", List.of(), List.of(), false, 0L, List.of());

    assertThat(MealDocument.of(bare)).isEqualTo("Name: Toast");
    assertThat(MealDocument.of(meal("Toast", "   ", List.of(), List.of())))
        .doesNotContain("Notes")
        .doesNotContain("Tags")
        .doesNotContain("Ingredients")
        .doesNotContain("example.com");
  }

  @Test
  void collapsesWhitespaceInNotesSoANoteCannotImitateAField() {
    var text = MealDocument.of(meal("Stew", "Good.\n\nTags:   evil\tone", List.of(), List.of()));

    assertThat(text)
        .isEqualTo("Name: Stew\nOccasions: BIRTHDAY, HOLIDAY\nNotes: Good. Tags: evil one");
  }

  @Test
  void theSameMealGivesTheSameTextAndHash() {
    var a = meal("Stew", "n", List.of("t"), List.of("i"));
    var b = meal("Stew", "n", List.of("t"), List.of("i"));

    assertThat(MealDocument.of(a)).isEqualTo(MealDocument.of(b));
    assertThat(MealDocument.hash(MealDocument.of(a), "m@768"))
        .isEqualTo(MealDocument.hash(MealDocument.of(b), "m@768"))
        .hasSize(64);
  }

  @Test
  void aDifferentModelGivesADifferentHash() {
    assertThat(MealDocument.hash("text", "model-a@768"))
        .isNotEqualTo(MealDocument.hash("text", "model-b@768"));
    assertThat(MealDocument.hash("text", "model-a@768"))
        .isNotEqualTo(MealDocument.hash("text", "model-a@512"));
  }

  @Test
  void aChangedIngredientOrTagChangesTheHash() {
    var base = meal("Stew", "n", List.of("t"), List.of("i"));

    assertThat(MealDocument.hash(MealDocument.of(base), "m"))
        .isNotEqualTo(
            MealDocument.hash(
                MealDocument.of(meal("Stew", "n", List.of("t"), List.of("i", "j"))), "m"))
        .isNotEqualTo(
            MealDocument.hash(MealDocument.of(meal("Stew", "n", List.of("u"), List.of("i"))), "m"));
  }

  @Test
  void longNotesAreCutToTheLimitAndTheNameSurvives() {
    var text = MealDocument.of(meal("Stew", "x".repeat(3_000), List.of("t"), List.of("i")));

    assertThat(text).hasSize(MealDocument.MAX_CHARS).startsWith("Name: Stew\nTags: t");
  }

  @Test
  void shortTextIsNotChanged() {
    assertThat(MealDocument.cap("short")).isEqualTo("short");
    assertThat(MealDocument.cap("y".repeat(MealDocument.MAX_CHARS)))
        .hasSize(MealDocument.MAX_CHARS);
  }

  @Test
  void theCutNeverSplitsASurrogatePair() {
    // The emoji is two chars; it would straddle the limit.
    String text = "a".repeat(MealDocument.MAX_CHARS - 1) + "🍲" + "tail";

    String capped = MealDocument.cap(text);

    assertThat(capped).hasSize(MealDocument.MAX_CHARS - 1).doesNotContain("\uD83C");
    assertThat(MealDocument.cap("a".repeat(MealDocument.MAX_CHARS) + "🍲"))
        .hasSize(MealDocument.MAX_CHARS);
  }
}
