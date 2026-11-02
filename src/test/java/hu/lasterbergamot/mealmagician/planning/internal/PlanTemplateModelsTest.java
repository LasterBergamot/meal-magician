package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The template page models: the addresses the forms post to, the rename card's state, the empty
 * state of the list and the message keys. None of them touches the database.
 */
class PlanTemplateModelsTest {

  private static TemplatePage page(TemplateScope scope, String renameError) {
    return new TemplatePage(7L, "Stew week", scope, List.of(), 3L, "Stew week", renameError, false);
  }

  @Test
  void theActionsOfATemplateAreUnderItsAddress() {
    TemplatePage page = page(TemplateScope.DAY, null);

    assertThat(page.href()).isEqualTo("/plan/templates/7");
    assertThat(page.renameAction()).isEqualTo("/plan/templates/7/rename");
    assertThat(page.deleteAction()).isEqualTo("/plan/templates/7/delete?version=3");
  }

  @Test
  void theScopeHasItsOwnBadgeKey() {
    assertThat(page(TemplateScope.DAY, null).scopeKey()).isEqualTo("plan.templates.scope.day");
    assertThat(page(TemplateScope.WEEK, null).scopeKey()).isEqualTo("plan.templates.scope.week");
  }

  @Test
  void aNoErrorRenameHasNoFieldErrorAndNoWarning() {
    TemplatePage page = page(TemplateScope.DAY, null);

    assertThat(page.staleRename()).isFalse();
    assertThat(page.fieldError()).isNull();
  }

  @Test
  void aNameErrorIsShownUnderTheFieldAndIsNotAWarning() {
    TemplatePage page = page(TemplateScope.DAY, "plan.templates.name.required");

    assertThat(page.staleRename()).isFalse();
    assertThat(page.fieldError()).isEqualTo("plan.templates.name.required");
  }

  @Test
  void aStaleRenameIsAWarningAndNeverAFieldError() {
    TemplatePage page = page(TemplateScope.DAY, TemplatePage.CONFLICT_KEY);

    assertThat(page.staleRename()).isTrue();
    assertThat(page.fieldError()).isNull();
  }

  @Test
  void aRejectedRenameKeepsWhatWasTypedAndSetsTheError() {
    TemplatePage rejected =
        page(TemplateScope.WEEK, null).withRename("  Kept  ", "plan.templates.name.long");

    assertThat(rejected.renameName()).isEqualTo("  Kept  ");
    assertThat(rejected.renameError()).isEqualTo("plan.templates.name.long");
    assertThat(rejected.name()).isEqualTo("Stew week");
    assertThat(rejected.version()).isEqualTo(3L);
    assertThat(rejected.scope()).isEqualTo(TemplateScope.WEEK);
  }

  @Test
  void theConflictFlagIsSetAndEverythingElseStaysAsItWas() {
    TemplatePage stale = page(TemplateScope.DAY, null).withConflict();

    assertThat(stale.conflict()).isTrue();
    assertThat(stale.renameName()).isEqualTo("Stew week");
    assertThat(stale.renameError()).isNull();
    assertThat(stale.id()).isEqualTo(7L);
  }

  @Test
  void aRenameErrorKeyFollowsTheProblem() {
    assertThat(TemplatePage.renameErrorKey(PlanProblem.NAME_TOO_LONG))
        .isEqualTo("plan.templates.name.long");
    assertThat(TemplatePage.renameErrorKey(PlanProblem.NAME_REQUIRED))
        .isEqualTo("plan.templates.name.required");
  }

  @Test
  void aRowLinksToItsTemplate() {
    assertThat(new TemplateRow(7L, "Soups", 2).href()).isEqualTo("/plan/templates/7");
  }

  @Test
  void theListIsEmptyOnlyWhenBothGroupsAre() {
    TemplateRow row = new TemplateRow(1L, "Soups", 1);

    assertThat(new TemplatesPage(List.of(), List.of()).empty()).isTrue();
    assertThat(new TemplatesPage(List.of(row), List.of()).empty()).isFalse();
    assertThat(new TemplatesPage(List.of(), List.of(row)).empty()).isFalse();
  }

  @Test
  void aLineUsesTheSlotsWordKey() {
    assertThat(new TemplateLine("LUNCH", "Stew", false, null).labelKey()).isEqualTo("slot.lunch");
  }

  @Test
  void theTemplateKeysExistInBothBundles() throws IOException {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    List<String> keys =
        List.of(
            "plan.templates.scope.day",
            "plan.templates.scope.week",
            "plan.templates.name.required",
            "plan.templates.name.long",
            TemplatePage.CONFLICT_KEY);
    for (String key : keys) {
      assertThat(hu.getProperty(key)).as("hu %s", key).isNotBlank();
      assertThat(en.getProperty(key)).as("en %s", key).isNotBlank();
    }
  }

  private static Properties bundle(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = PlanTemplateModelsTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("bundle %s on the classpath", name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
