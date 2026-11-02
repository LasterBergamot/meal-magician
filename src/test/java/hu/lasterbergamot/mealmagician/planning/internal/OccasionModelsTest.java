package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.InvalidPlanException;
import hu.lasterbergamot.mealmagician.planning.OccasionDetails;
import hu.lasterbergamot.mealmagician.planning.OccasionView;
import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OccasionModelsTest {

  private static final List<FormOption> OPTIONS =
      List.of(new FormOption("BIRTHDAY", "Születésnap"));

  @Test
  void aBlankFormStartsWithBirthdayAndNoValues() {
    OccasionForm blank = OccasionForm.blank();

    assertThat(blank.editing()).isFalse();
    assertThat(blank.name()).isEmpty();
    assertThat(blank.date()).isEmpty();
    assertThat(blank.type()).isEqualTo("BIRTHDAY");
    assertThat(blank.action()).isEqualTo("/occasions");
    assertThat(blank.backHref()).isEqualTo("/occasions");
  }

  @Test
  void aStoredOccasionFillsTheFormAndPointsAtItsOwnAddress() {
    OccasionView view =
        new OccasionView(
            7, "Anna", LocalDate.of(2026, 12, 24), OccasionType.HOLIDAY, null, List.of(), 2);

    OccasionForm form = OccasionForm.of(view);

    assertThat(form.editing()).isTrue();
    assertThat(form.id()).isEqualTo(7L);
    assertThat(form.version()).isEqualTo(2);
    assertThat(form.date()).isEqualTo("2026-12-24");
    assertThat(form.type()).isEqualTo("HOLIDAY");
    assertThat(form.notes()).as("a missing note is an empty text").isEmpty();
    assertThat(form.action()).isEqualTo("/occasions/7");
    assertThat(form.backHref()).isEqualTo("/occasions/7");
  }

  @Test
  void aStoredNoteIsKeptInTheForm() {
    OccasionView view =
        new OccasionView(
            7,
            "Anna",
            LocalDate.of(2026, 12, 24),
            OccasionType.PARTY,
            "Hozd a tortát",
            List.of(),
            0);

    assertThat(OccasionForm.of(view).notes()).isEqualTo("Hozd a tortát");
  }

  @Test
  void detailsCarryTheTypedValuesWhenTheyAreValid() {
    OccasionForm form = new OccasionForm(null, 0, "Szenteste", "2026-12-24", "HOLIDAY", "Halászlé");

    OccasionDetails details = form.details();

    assertThat(details.name()).isEqualTo("Szenteste");
    assertThat(details.date()).isEqualTo(LocalDate.of(2026, 12, 24));
    assertThat(details.type()).isEqualTo(OccasionType.HOLIDAY);
    assertThat(details.notes()).isEqualTo("Halászlé");
  }

  @Test
  void aMalformedDateOrKindBecomesNullSoTheServiceRefusesIt() {
    OccasionDetails badDate = new OccasionForm(null, 0, "A", "x", "PARTY", "").details();
    OccasionDetails blankDate = new OccasionForm(null, 0, "A", "", "PARTY", "").details();
    OccasionDetails badType = new OccasionForm(null, 0, "A", "2026-12-24", "FOO", "").details();

    assertThat(badDate.date()).isNull();
    assertThat(blankDate.date()).isNull();
    assertThat(badType.type()).isNull();
  }

  @Test
  void parseDateReadsIsoDatesAndRefusesEverythingElse() {
    assertThat(OccasionForm.parseDate("2026-12-24")).isEqualTo(LocalDate.of(2026, 12, 24));
    assertThat(OccasionForm.parseDate(null)).isNull();
    assertThat(OccasionForm.parseDate("   ")).isNull();
    assertThat(OccasionForm.parseDate("24.12.2026")).isNull();
  }

  @Test
  void parseTypeReadsEnumNamesAndRefusesEverythingElse() {
    assertThat(OccasionForm.parseType("PARTY")).isEqualTo(OccasionType.PARTY);
    assertThat(OccasionForm.parseType(null)).isNull();
    assertThat(OccasionForm.parseType("")).isNull();
    assertThat(OccasionForm.parseType("FOO")).isNull();
  }

  @ParameterizedTest
  @EnumSource(OccasionType.class)
  void everyKindHasAMessageKeyAndTheCardsReturnTheirHrefs(OccasionType type) {
    OccasionCard card = new OccasionCard(3, "Buli", "Monday", type, 0, false);

    assertThat(card.href()).isEqualTo("/occasions/3");
    assertThat(card.typeKey()).isEqualTo("meal.occasion." + type.name().toLowerCase());
  }

  @Test
  void theListIsEmptyOnlyWithoutANextOccasionOrAnyPastOne() {
    OccasionCard card = new OccasionCard(1, "A", "d", OccasionType.OTHER, 0, false);

    assertThat(new OccasionsPage(null, List.of(), List.of()).empty()).isTrue();
    assertThat(new OccasionsPage(card, List.of(), List.of()).empty()).isFalse();
    assertThat(new OccasionsPage(null, List.of(), List.of(card)).empty()).isFalse();
  }

  @Test
  void aMenuLineKnowsWhichMovesAreOpenAtEachPlace() {
    MenuLine first = new MenuLine(10, 4, "Lencse", false, 0, 3);
    MenuLine middle = new MenuLine(11, 5, "Pasta", true, 1, 3);
    MenuLine last = new MenuLine(12, 6, "Tea", false, 2, 3);

    assertThat(first.canMoveUp()).isFalse();
    assertThat(first.canMoveDown()).isTrue();
    assertThat(middle.canMoveUp()).isTrue();
    assertThat(middle.canMoveDown()).isTrue();
    assertThat(middle.upPosition()).isZero();
    assertThat(middle.downPosition()).isEqualTo(2);
    assertThat(last.canMoveUp()).isTrue();
    assertThat(last.canMoveDown()).isFalse();
    assertThat(last.mealHref()).isEqualTo("/meals/6");
  }

  @Test
  void theDetailPageBuildsItsAddresses() {
    OccasionDetailPage page =
        new OccasionDetailPage(
            7, "Anna", "Monday", OccasionType.HOLIDAY, null, List.of(), 2, false);

    assertThat(page.href()).isEqualTo("/occasions/7");
    assertThat(page.editHref()).isEqualTo("/occasions/7/edit");
    assertThat(page.deleteAction()).isEqualTo("/occasions/7/delete?version=2");
    assertThat(page.typeKey()).isEqualTo("meal.occasion.holiday");
    assertThat(page.withConflict().conflict()).isTrue();
    assertThat(page.withConflict().name()).isEqualTo("Anna");
  }

  @Test
  void aRejectedNameIsShownUnderTheName() {
    OccasionFormPage page =
        OccasionFormPage.rejected(
            OccasionForm.blank(), OPTIONS, new InvalidPlanException(PlanProblem.NAME_REQUIRED));

    assertThat(page.error("name")).isEqualTo("occasion.error.name.required");
    assertThat(page.error("date")).isNull();
  }

  @Test
  void eachRefusedRuleIsShownUnderItsOwnField() {
    assertThat(refuse(PlanProblem.NAME_TOO_LONG))
        .containsExactly("name", "occasion.error.name.long");
    assertThat(refuse(PlanProblem.DATE_REQUIRED)).containsExactly("date", "occasion.error.date");
    assertThat(refuse(PlanProblem.TYPE_REQUIRED)).containsExactly("type", "occasion.error.type");
    assertThat(refuse(PlanProblem.NOTES_TOO_LONG))
        .containsExactly("notes", "occasion.error.notes.long");
  }

  @Test
  void aRuleThatBelongsToAnotherFormFallsBackToTheName() {
    assertThat(refuse(PlanProblem.SAME_TARGET))
        .containsExactly("name", "occasion.error.name.required");
  }

  @Test
  void aRejectedFormKeepsWhatWasTyped() {
    OccasionForm typed = new OccasionForm(null, 0, "", "x", "FOO", "");

    OccasionFormPage page =
        OccasionFormPage.rejected(
            typed, OPTIONS, new InvalidPlanException(PlanProblem.NAME_REQUIRED));

    assertThat(page.form()).isSameAs(typed);
    assertThat(page.conflict()).isFalse();
    assertThat(page.typeOptions()).isSameAs(OPTIONS);
  }

  @Test
  void aStaleFormIsMarkedAsAConflictWithoutAFieldError() {
    OccasionFormPage page = OccasionFormPage.stale(OccasionForm.blank(), OPTIONS);

    assertThat(page.conflict()).isTrue();
    assertThat(page.error("name")).isNull();
  }

  @Test
  void aPlainFormHasNoErrors() {
    OccasionFormPage page = OccasionFormPage.of(OccasionForm.blank(), OPTIONS);

    assertThat(page.conflict()).isFalse();
    assertThat(page.error("name")).isNull();
  }

  @Test
  void theTitleSaysWhetherTheFormCreatesOrEdits() {
    OccasionFormPage blank = OccasionFormPage.of(OccasionForm.blank(), OPTIONS);
    OccasionFormPage editing =
        OccasionFormPage.of(new OccasionForm(3L, 0, "A", "2026-01-01", "PARTY", ""), OPTIONS);

    assertThat(blank.titleKey()).isEqualTo("occasion.form.title.new");
    assertThat(editing.titleKey()).isEqualTo("occasion.form.title.edit");
  }

  @Test
  void everyMessageKeyTheFormsUseExistsInBothLanguages() throws IOException {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    List<String> keys =
        List.of(
            "occasion.error.name.required",
            "occasion.error.name.long",
            "occasion.error.date",
            "occasion.error.type",
            "occasion.error.notes.long",
            "occasion.form.title.new",
            "occasion.form.title.edit");
    for (String key : keys) {
      assertThat(hu.getProperty(key)).as("hu %s", key).isNotBlank();
      assertThat(en.getProperty(key)).as("en %s", key).isNotBlank();
    }
  }

  /** The field and key a refusal of the given problem produces. */
  private static List<String> refuse(PlanProblem problem) {
    OccasionFormPage page =
        OccasionFormPage.rejected(OccasionForm.blank(), OPTIONS, new InvalidPlanException(problem));
    return List.of(page.errorField(), page.errorKey());
  }

  private static Properties bundle(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = OccasionModelsTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("bundle %s on the classpath", name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
