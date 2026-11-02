package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class OccasionMenuModelsTest {

  private static OccasionDetailPage page(List<MenuLine> menu) {
    return new OccasionDetailPage(
        7, "Karácsony", "Monday", OccasionType.HOLIDAY, null, menu, 2, false, "Karácsony", null);
  }

  @Test
  void theDetailPageBuildsTheMenuAddresses() {
    OccasionDetailPage page = page(List.of());

    assertThat(page.pickHref()).isEqualTo("/occasions/7/pick");
    assertThat(page.moveAction(5)).isEqualTo("/occasions/7/menu/5/move");
    assertThat(page.removeAction(5)).isEqualTo("/occasions/7/menu/5/remove");
    assertThat(page.saveAction()).isEqualTo("/occasions/7/template");
  }

  @Test
  void anEmptyMenuCannotBeSavedButOneWithAMealCan() {
    assertThat(page(List.of()).canSave()).isFalse();
    assertThat(page(List.of(new MenuLine(1, 2, "Bejgli", false, 0, 1))).canSave()).isTrue();
  }

  @Test
  void withSaveKeepsTheTypedNameAndTheMessage() {
    OccasionDetailPage rejected = page(List.of()).withSave("", "occasion.template.error.empty");

    assertThat(rejected.saveName()).isEmpty();
    assertThat(rejected.saveError()).isEqualTo("occasion.template.error.empty");
    assertThat(rejected.name()).isEqualTo("Karácsony");
  }

  @Test
  void withConflictKeepsTheSaveFields() {
    OccasionDetailPage conflicted = page(List.of()).withSave("Typed", "x").withConflict();

    assertThat(conflicted.conflict()).isTrue();
    assertThat(conflicted.saveName()).isEqualTo("Typed");
    assertThat(conflicted.saveError()).isEqualTo("x");
  }

  @Test
  void eachRefusedSaveHasItsOwnMessageThatExistsInBothLanguages() throws IOException {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");

    assertThat(OccasionDetailPage.saveErrorKey(PlanProblem.NOTHING_TO_COPY))
        .isEqualTo("occasion.template.error.empty");
    assertThat(OccasionDetailPage.saveErrorKey(PlanProblem.NAME_TOO_LONG))
        .isEqualTo("occasion.template.error.name.long");
    assertThat(OccasionDetailPage.saveErrorKey(PlanProblem.NAME_REQUIRED))
        .isEqualTo("occasion.template.error.name.required");
    for (PlanProblem problem :
        List.of(
            PlanProblem.NOTHING_TO_COPY, PlanProblem.NAME_TOO_LONG, PlanProblem.NAME_REQUIRED)) {
      String key = OccasionDetailPage.saveErrorKey(problem);
      assertThat(hu.getProperty(key)).as("hu %s", key).isNotBlank();
      assertThat(en.getProperty(key)).as("en %s", key).isNotBlank();
    }
  }

  @Test
  void theTemplatePageBuildsItsAddressesAndKeepsTheTypedDate() {
    OccasionTemplatePage page =
        new OccasionTemplatePage(4, "Menü", OccasionType.PARTY, null, List.of(), "", null);

    assertThat(page.href()).isEqualTo("/occasions/templates/4");
    assertThat(page.useAction()).isEqualTo("/occasions/templates/4/use");
    assertThat(page.deleteAction()).isEqualTo("/occasions/templates/4/delete");
    assertThat(page.typeKey()).isEqualTo("meal.occasion.party");
    OccasionTemplatePage rejected = page.withUse("x", "occasion.error.date");
    assertThat(rejected.useDate()).isEqualTo("x");
    assertThat(rejected.useError()).isEqualTo("occasion.error.date");
    assertThat(rejected.name()).isEqualTo("Menü");
  }

  @Test
  void rowsAndLinesBuildTheirAddresses() {
    OccasionTemplateRow row = new OccasionTemplateRow(4, "Menü", OccasionType.HOLIDAY, 3);
    MealLine line = new MealLine(9, "Bejgli", false);

    assertThat(row.href()).isEqualTo("/occasions/templates/4");
    assertThat(row.typeKey()).isEqualTo("meal.occasion.holiday");
    assertThat(line.mealHref()).isEqualTo("/meals/9");
  }

  @Test
  void thePickPageBuildsItsAddresses() {
    OccasionPickPage page = new OccasionPickPage(7, "", List.of(), false, true, PickQuick.empty());

    assertThat(page.pickHref()).isEqualTo("/occasions/7/pick");
    assertThat(page.menuAction()).isEqualTo("/occasions/7/menu");
    assertThat(page.quickAction()).isEqualTo("/occasions/7/menu/quick");
    assertThat(page.backHref()).isEqualTo("/occasions/7");
  }

  private static Properties bundle(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = OccasionMenuModelsTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("bundle %s on the classpath", name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
