package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.catalog.MealProblem;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import hu.lasterbergamot.mealmagician.shared.web.FormOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanEditingModelsTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private static SlotEditor editor(int position, int groupSize) {
    return new SlotEditor(
        7,
        3,
        12,
        "Gulyás",
        false,
        MONDAY.plusDays(2),
        SlotType.DINNER,
        "Szerda, nov. 4.",
        position,
        groupSize,
        List.of(new FormOption("2026-11-02", "Hétfő")),
        List.of(new FormOption("LUNCH", "Ebéd")),
        "régi",
        null);
  }

  @Test
  void pickQuickStartsEmptyAndMapsTheNameProblemsToMessageKeys() {
    assertThat(PickQuick.empty()).isEqualTo(new PickQuick("", null));
    assertThat(PickQuick.errorKey(MealProblem.NAME_TOO_LONG)).isEqualTo("meal.error.name.long");
    assertThat(PickQuick.errorKey(MealProblem.NAME_REQUIRED)).isEqualTo("meal.error.name.required");
  }

  @Test
  void aPickPageKnowsItsHiddenFieldValuesLabelAndWeek() {
    PickPage page =
        new PickPage(
            MONDAY.plusDays(2),
            SlotType.SNACK,
            "label",
            "",
            List.of(),
            false,
            true,
            PickQuick.empty());

    assertThat(page.dateValue()).isEqualTo("2026-11-04");
    assertThat(page.slotName()).isEqualTo("SNACK");
    assertThat(page.labelKey()).isEqualTo("slot.snack");
    assertThat(page.weekHref()).isEqualTo("/plan?week=2026-W45");
    assertThat(PickPage.LIMIT).isEqualTo(20);
  }

  @Test
  void aSlotEditorBuildsItsAddressesAndValues() {
    SlotEditor editor = editor(0, 1);

    assertThat(editor.action("note")).isEqualTo("/plan/slots/7/note");
    assertThat(editor.removeAction()).isEqualTo("/plan/slots/7/remove?version=3");
    assertThat(editor.mealHref()).isEqualTo("/meals/12");
    assertThat(editor.dateValue()).isEqualTo("2026-11-04");
    assertThat(editor.slotName()).isEqualTo("DINNER");
    assertThat(editor.labelKey()).isEqualTo("slot.dinner");
    assertThat(editor.weekHref()).isEqualTo("/plan?week=2026-W45");
  }

  @Test
  void aSlotEditorKnowsWhichWaysItCanMove() {
    assertThat(editor(0, 1).canMoveUp()).isFalse();
    assertThat(editor(0, 1).canMoveDown()).isFalse();
    assertThat(editor(0, 3).canMoveUp()).isFalse();
    assertThat(editor(0, 3).canMoveDown()).isTrue();
    assertThat(editor(1, 3).canMoveUp()).isTrue();
    assertThat(editor(1, 3).canMoveDown()).isTrue();
    assertThat(editor(2, 3).canMoveUp()).isTrue();
    assertThat(editor(2, 3).canMoveDown()).isFalse();
    assertThat(editor(1, 3).upPosition()).isZero();
    assertThat(editor(1, 3).downPosition()).isEqualTo(2);
  }

  @Test
  void aRejectedNoteKeepsTheTypedTextAndThePostedVersion() {
    SlotEditor rejected = editor(1, 3).rejectedNote("túl hosszú", 2);

    assertThat(rejected.note()).isEqualTo("túl hosszú");
    assertThat(rejected.version()).isEqualTo(2);
    assertThat(rejected.noteError()).isEqualTo("plan.editor.note.long");
    assertThat(rejected.slotId()).isEqualTo(7);
    assertThat(rejected.position()).isEqualTo(1);
  }

  @Test
  void aSlotGroupBuildsItsAddKeyAndPickAddress() {
    SlotGroup group = new SlotGroup(MONDAY, SlotType.LUNCH, List.of());

    assertThat(group.addKey()).isEqualTo("plan.slot.add.lunch");
    assertThat(group.pickHref()).isEqualTo("/plan/pick?date=2026-11-02&slot=LUNCH");
  }

  @Test
  void aWeekPageCanSwitchTheConflictBannerOn() {
    IsoWeek week = new IsoWeek(MONDAY);
    DayPlan day =
        new DayPlan(
            MONDAY,
            "n",
            "l",
            "s",
            "f",
            false,
            Arrays.stream(SlotType.values())
                .map(type -> new SlotGroup(MONDAY, type, List.of()))
                .toList());
    WeekPage page = new WeekPage(week, "t", "r", true, List.of(day), false);

    assertThat(page.conflict()).isFalse();
    assertThat(page.withConflict().conflict()).isTrue();
    assertThat(page.withConflict().title()).isEqualTo("t");
  }
}
