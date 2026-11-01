package hu.lasterbergamot.mealmagician.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The input rules, without a database: every refusal names the problem it reports. */
class MealRulesTest {

  @Test
  void aNameIsTrimmed() {
    assertThat(MealRules.name("  Lentil soup ")).isEqualTo("Lentil soup");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void aBlankNameIsRequired(String name) {
    assertThatThrownBy(() -> MealRules.name(name))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.NAME_REQUIRED);
  }

  @Test
  void aNameOfExactlyTheMaximumLengthIsAccepted() {
    String longest = "x".repeat(MealRules.NAME_MAX);

    assertThat(MealRules.name(longest)).isEqualTo(longest);
  }

  @Test
  void aNameOneCharacterTooLongIsRefused() {
    assertThatThrownBy(() -> MealRules.name("x".repeat(MealRules.NAME_MAX + 1)))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.NAME_TOO_LONG);
  }

  @Test
  void blankNotesAreStoredAsNoNotes() {
    assertThat(MealRules.notes(null)).isNull();
    assertThat(MealRules.notes("   ")).isNull();
  }

  @Test
  void notesAreTrimmed() {
    assertThat(MealRules.notes("  Use the red lentils. ")).isEqualTo("Use the red lentils.");
  }

  @Test
  void notesOneCharacterTooLongAreRefused() {
    assertThatThrownBy(() -> MealRules.notes("x".repeat(MealRules.NOTES_MAX + 1)))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.NOTES_TOO_LONG);
  }

  @Test
  void blankRecipeLinkIsStoredAsNoLink() {
    assertThat(MealRules.recipeUrl(null)).isNull();
    assertThat(MealRules.recipeUrl("  ")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.com/lentil",
        "http://example.com/lentil",
        "HTTPS://example.com/lentil"
      })
  void webLinksAreAccepted(String link) {
    assertThat(MealRules.recipeUrl("  " + link + " ")).isEqualTo(link);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "data:text/html,hello",
        "ftp://example.com/file",
        "example.com/lentil",
        "not a link",
        "https:///no-host",
        "https://"
      })
  void linksThatAreNotWebAddressesAreRefused(String link) {
    assertThatThrownBy(() -> MealRules.recipeUrl(link))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.RECIPE_URL_INVALID);
  }

  @Test
  void aLinkTooLongIsRefusedEvenIfItIsWeb() {
    String link = "https://example.com/" + "x".repeat(MealRules.RECIPE_URL_MAX);

    assertThatThrownBy(() -> MealRules.recipeUrl(link))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.RECIPE_URL_INVALID);
  }

  @Test
  void aNullTagListMeansNoTags() {
    assertThat(MealRules.tags(null)).isEmpty();
  }

  @Test
  void tagNamesAreCleanedAndRepeatsWithDifferentCaseAreOneTag() {
    var tags = MealRules.tags(List.of("  Quick   soup ", "VEGAN", "quick soup", "vegan"));

    assertThat(tags)
        .containsExactly(
            java.util.Map.entry("quick soup", "Quick soup"), java.util.Map.entry("vegan", "VEGAN"));
  }

  @Test
  void aTagNameOfExactlyTheMaximumLengthIsAccepted() {
    String longest = "x".repeat(MealRules.TAG_MAX);

    assertThat(MealRules.tagName(longest)).isEqualTo(longest);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void aBlankTagNameIsRefused(String name) {
    assertThatThrownBy(() -> MealRules.tagName(name))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.TAG_INVALID);
  }

  @Test
  void aTagNameOneCharacterTooLongIsRefused() {
    assertThatThrownBy(() -> MealRules.tagName("x".repeat(MealRules.TAG_MAX + 1)))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.TAG_INVALID);
  }

  @Test
  void aBlankTagInTheListIsRefused() {
    assertThatThrownBy(() -> MealRules.tags(Arrays.asList("vegan", null)))
        .isInstanceOf(InvalidMealException.class)
        .extracting(e -> ((InvalidMealException) e).problem())
        .isEqualTo(MealProblem.TAG_INVALID);
  }

  @Test
  void tagKeyIgnoresCaseAndSpacing() {
    assertThat(MealRules.tagKey("  Quick   SOUP ")).isEqualTo("quick soup");
  }

  @Test
  void aBlankSearchTextMeansNoNameFilter() {
    assertThat(MealRules.namePattern(null)).isNull();
    assertThat(MealRules.namePattern("  ")).isNull();
  }

  @Test
  void aSearchTextBecomesACaseInsensitiveContainsPattern() {
    assertThat(MealRules.namePattern("  Soup ")).isEqualTo("%soup%");
  }

  @Test
  void wildcardsAndTheEscapeCharacterInTheSearchTextAreEscaped() {
    assertThat(MealRules.namePattern("50% _ wow!")).isEqualTo("%50!% !_ wow!!%");
  }
}
