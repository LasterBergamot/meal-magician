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
  void aNoteOverTheLimitIsRefused() {
    assertThatThrownBy(() -> PlanRules.note("x".repeat(PlanRules.NOTE_MAX + 1)))
        .isInstanceOf(InvalidPlanException.class)
        .extracting(e -> ((InvalidPlanException) e).problem())
        .isEqualTo(PlanProblem.NOTE_TOO_LONG);
  }
}
