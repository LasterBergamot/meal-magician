package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.PlanProblem;
import hu.lasterbergamot.mealmagician.planning.TemplateScope;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The reuse sheet's view models: addresses, message keys and the keys' presence in both bundles.
 */
class PlanReuseModelsTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private static ReusePage page(TemplateScope scope) {
    return new ReusePage(scope, MONDAY, "h", null, 0, "t", List.of(), List.of(), ReuseSave.empty());
  }

  @Test
  void aDayPageBuildsItsAddressesAndKeys() {
    ReusePage page = page(TemplateScope.DAY);

    assertThat(page.forDay()).isTrue();
    assertThat(page.empty()).isTrue();
    assertThat(page.scopeName()).isEqualTo("DAY");
    assertThat(page.dateValue()).isEqualTo("2026-11-02");
    assertThat(page.fromValue()).isEqualTo("2026-11-02");
    assertThat(page.copyAction("MERGE")).isEqualTo("/plan/days/copy?mode=MERGE");
    assertThat(page.applyAction(7, "MERGE")).isEqualTo("/plan/templates/7/apply?mode=MERGE");
    assertThat(page.applyReplaceAction(7))
        .isEqualTo("/plan/templates/7/apply?mode=REPLACE&date=2026-11-02");
    assertThat(page.weekHref()).isEqualTo("/plan?week=2026-W45");
    assertThat(page.emptyKey()).isEqualTo("plan.reuse.empty.day");
    assertThat(page.copyHeadingKey()).isEqualTo("plan.reuse.copy.day.heading");
    assertThat(page.replaceTitleKey()).isEqualTo("plan.reuse.replace.day.title");
    assertThat(page.replaceMessageKey()).isEqualTo("plan.reuse.replace.day.message");
    assertThat(page.saveHeadingKey()).isEqualTo("plan.reuse.save.day.heading");
    assertThat(page.applyHeadingKey()).isEqualTo("plan.reuse.apply.day.heading");
    assertThat(page.applyNoneKey()).isEqualTo("plan.reuse.apply.none.day");
    assertThat(page.applyReplaceTitleKey()).isEqualTo("plan.reuse.apply.replace.day.title");
  }

  @Test
  void aWeekPageBuildsItsAddressesAndKeys() {
    ReusePage page =
        new ReusePage(
            TemplateScope.WEEK, MONDAY, "h", "r", 3, "t", List.of(), List.of(), ReuseSave.empty());

    assertThat(page.forDay()).isFalse();
    assertThat(page.empty()).isFalse();
    assertThat(page.scopeName()).isEqualTo("WEEK");
    assertThat(page.fromValue()).isEqualTo("2026-W45");
    assertThat(page.copyAction("REPLACE")).isEqualTo("/plan/weeks/copy?mode=REPLACE");
    assertThat(page.emptyKey()).isEqualTo("plan.reuse.empty.week");
    assertThat(page.copyHeadingKey()).isEqualTo("plan.reuse.copy.week.heading");
    assertThat(page.replaceTitleKey()).isEqualTo("plan.reuse.replace.week.title");
    assertThat(page.replaceMessageKey()).isEqualTo("plan.reuse.replace.week.message");
    assertThat(page.saveHeadingKey()).isEqualTo("plan.reuse.save.week.heading");
    assertThat(page.applyHeadingKey()).isEqualTo("plan.reuse.apply.week.heading");
    assertThat(page.applyNoneKey()).isEqualTo("plan.reuse.apply.none.week");
    assertThat(page.applyReplaceTitleKey()).isEqualTo("plan.reuse.apply.replace.week.title");
  }

  @Test
  void aSaveFormMapsEachProblemToAKey() {
    assertThat(ReuseSave.empty()).isEqualTo(new ReuseSave("", null));
    assertThat(ReuseSave.errorKey(PlanProblem.NAME_REQUIRED))
        .isEqualTo("plan.reuse.save.name.required");
    assertThat(ReuseSave.errorKey(PlanProblem.NAME_TOO_LONG))
        .isEqualTo("plan.reuse.save.name.long");
    assertThat(ReuseSave.errorKey(PlanProblem.NOTHING_TO_COPY)).isEqualTo("plan.reuse.nothing");
  }

  @Test
  void aRefusedCopyMapsEachProblemToAKey() {
    assertThat(PlanResponses.reuseErrorKey(PlanProblem.SAME_TARGET))
        .isEqualTo("plan.reuse.error.same");
    assertThat(PlanResponses.reuseErrorKey(PlanProblem.NOTHING_TO_COPY))
        .isEqualTo("plan.reuse.nothing");
  }

  @Test
  void isSamePageComparesPathAndQueryOnly() {
    assertThat(PlanResponses.isSamePage(null, "/plan")).isFalse();
    assertThat(PlanResponses.isSamePage("http://[x", "/plan")).isFalse();
    assertThat(
            PlanResponses.isSamePage(
                "http://h/plan?week=2026-W45", "/plan?week=2026-W45#day-2026-11-02"))
        .isTrue();
    assertThat(PlanResponses.isSamePage("http://h/plan?week=2026-W45", "/plan?week=2026-W46"))
        .isFalse();
    assertThat(PlanResponses.isSamePage("http://h/occasions/7", "/occasions/7")).isTrue();
  }

  @Test
  void theSmallModelsBuildTheirStrings() {
    OccasionChip chip = new OccasionChip(7, "Születésnap", "Szerda", OccasionType.BIRTHDAY);
    assertThat(chip.href()).isEqualTo("/occasions/7");
    assertThat(chip.typeKey()).isEqualTo("meal.occasion.birthday");

    IsoWeek week = new IsoWeek(MONDAY);
    DayPlan day =
        new DayPlan(
            MONDAY,
            "n",
            "l",
            "s",
            "f",
            false,
            Arrays.stream(hu.lasterbergamot.mealmagician.planning.SlotType.values())
                .map(type -> new SlotGroup(MONDAY, type, List.<PlannedMeal>of()))
                .toList());
    assertThat(day.reuseHref()).isEqualTo("/plan/reuse/day?date=2026-11-02");
    WeekPage page = new WeekPage(week, "t", "r", true, List.of(day), false, List.of(), null);
    assertThat(page.reuseHref()).isEqualTo("/plan/reuse/week?week=2026-W45");
    assertThat(page.withReuseError("k").reuseError()).isEqualTo("k");
    assertThat(page.withReuseError("k").withConflict().reuseError()).isEqualTo("k");
    assertThat(page.withReuseError("k").withConflict().conflict()).isTrue();
  }

  @Test
  void everyReuseKeyExistsInBothLanguages() throws IOException {
    Properties hu = bundle("messages_hu.properties");
    Properties en = bundle("messages_en.properties");
    List<String> keys = new ArrayList<>();
    for (TemplateScope scope : TemplateScope.values()) {
      ReusePage page = page(scope);
      keys.addAll(
          List.of(
              page.emptyKey(),
              page.copyHeadingKey(),
              page.replaceTitleKey(),
              page.replaceMessageKey(),
              page.saveHeadingKey(),
              page.applyHeadingKey(),
              page.applyNoneKey(),
              page.applyReplaceTitleKey()));
    }
    for (PlanProblem problem : PlanProblem.values()) {
      keys.add(ReuseSave.errorKey(problem));
      keys.add(PlanResponses.reuseErrorKey(problem));
    }
    for (String key : keys) {
      assertThat(hu.getProperty(key)).as("hu %s", key).isNotBlank();
      assertThat(en.getProperty(key)).as("en %s", key).isNotBlank();
    }
  }

  private static Properties bundle(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = PlanReuseModelsTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("bundle %s on the classpath", name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
