package hu.lasterbergamot.mealmagician.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlanRulesTest {

  @Test
  void aNoteIsTrimmedAndKept() {
    assertThat(PlanRules.note("  Kids eat early ")).isEqualTo("Kids eat early");
  }

  @Test
  void aBlankOrMissingNoteIsNoNote() {
    assertThat(PlanRules.note(null)).isNull();
    assertThat(PlanRules.note("")).isNull();
    assertThat(PlanRules.note("   ")).isNull();
  }

  @Test
  void aNoteOfExactlyTheLimitIsAccepted() {
    String atLimit = "x".repeat(PlanRules.NOTE_MAX);

    assertThat(PlanRules.note(atLimit)).isEqualTo(atLimit);
  }

  @Test
  void aNameIsTrimmed() {
    assertThat(PlanRules.name("  x ")).isEqualTo("x");
  }

  @Test
  void aBlankOrMissingNameIsRefused() {
    assertThatThrownBy(() -> PlanRules.name("   "))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NAME_REQUIRED);
    assertThatThrownBy(() -> PlanRules.name(null))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NAME_REQUIRED);
  }

  @Test
  void aNameOfExactlyTheLimitIsAcceptedAndOneMoreIsRefused() {
    String atLimit = "x".repeat(PlanRules.NAME_MAX);

    assertThat(PlanRules.name(atLimit)).isEqualTo(atLimit);
    assertThatThrownBy(() -> PlanRules.name(atLimit + "x"))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NAME_TOO_LONG);
  }

  @Test
  void blankNotesAreNoNotes() {
    assertThat(PlanRules.notes(null)).isNull();
    assertThat(PlanRules.notes("  ")).isNull();
  }

  @Test
  void notesAreTrimmedAndKeptUpToTheLimit() {
    String atLimit = "x".repeat(PlanRules.NOTES_MAX);

    assertThat(PlanRules.notes(" a ")).isEqualTo("a");
    assertThat(PlanRules.notes(atLimit)).isEqualTo(atLimit);
  }

  @Test
  void notesOverTheLimitAreRefused() {
    assertThatThrownBy(() -> PlanRules.notes("x".repeat(PlanRules.NOTES_MAX + 1)))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NOTES_TOO_LONG);
  }

  @Test
  void aNoteOverTheLimitIsRefused() {
    assertThatThrownBy(() -> PlanRules.note("x".repeat(PlanRules.NOTE_MAX + 1)))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NOTE_TOO_LONG);
  }
}
