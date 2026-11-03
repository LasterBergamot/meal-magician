package hu.lasterbergamot.mealmagician;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import hu.lasterbergamot.mealmagician.catalog.MealNameLookup;
import hu.lasterbergamot.mealmagician.planning.SharedWeekReader;
import org.junit.jupiter.api.Test;

/**
 * Guards the two "unchecked readers" (PLAN.md D28): they answer without asking who is calling, so
 * only the module they were made for may use them. {@code SharedWeekReader} is for {@code sharing}
 * and {@code MealNameLookup} is for {@code planning}; a module's own classes may of course use its
 * own reader. A deliberately broken sample proves each rule can fail.
 */
class UncheckedReadersTest {

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("hu.lasterbergamot.mealmagician");

  private static ArchRule onlyThese(Class<?> reader, String... allowedPackages) {
    return noClasses()
        .that()
        .resideOutsideOfPackages(allowedPackages)
        .should()
        .dependOnClassesThat()
        .areAssignableTo(reader)
        .allowEmptyShould(true)
        .because("%s is an unchecked reader".formatted(reader.getSimpleName()));
  }

  private static final ArchRule SHARED_WEEK_READER =
      onlyThese(SharedWeekReader.class, "..sharing..", "..planning..");

  private static final ArchRule MEAL_NAME_LOOKUP =
      onlyThese(MealNameLookup.class, "..planning..", "..catalog..");

  @Test
  void onlySharingUsesTheSharedWeekReader() {
    SHARED_WEEK_READER.check(MAIN);
  }

  @Test
  void onlyPlanningUsesTheMealNameLookup() {
    MEAL_NAME_LOOKUP.check(MAIN);
  }

  @Test
  void theMainCodeActuallyContainsTheReaders() {
    assertThat(MAIN.contain(SharedWeekReader.class)).isTrue();
    assertThat(MAIN.contain(MealNameLookup.class)).isTrue();
  }

  @Test
  void aClassOutsideSharingUsingTheSharedWeekReaderIsFlagged() {
    JavaClasses sample = new ClassFileImporter().importClasses(BrokenSharedWeekUser.class);

    assertThatThrownBy(() -> SHARED_WEEK_READER.check(sample))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("SharedWeekReader");
  }

  @Test
  void aClassOutsidePlanningUsingTheMealNameLookupIsFlagged() {
    JavaClasses sample = new ClassFileImporter().importClasses(BrokenMealNameUser.class);

    assertThatThrownBy(() -> MEAL_NAME_LOOKUP.check(sample))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MealNameLookup");
  }

  /** Sample of what the rule forbids: a class in no allowed module holding the reader. */
  @SuppressWarnings("unused")
  static class BrokenSharedWeekUser {
    private SharedWeekReader reader;
  }

  /** Sample of what the rule forbids: a class in no allowed module holding the lookup. */
  @SuppressWarnings("unused")
  static class BrokenMealNameUser {
    private MealNameLookup lookup;
  }
}
