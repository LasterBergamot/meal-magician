package hu.lasterbergamot.mealmagician.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PlanParamsTest {

  private static void assertBadRequest(Runnable parse) {
    assertThatThrownBy(parse::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void aDateIsParsedStrictly() {
    assertBadRequest(() -> PlanParams.date(null));
    assertBadRequest(() -> PlanParams.date("x"));
    assertBadRequest(() -> PlanParams.date("2026-02-30"));
    assertThat(PlanParams.date("2026-11-04")).isEqualTo(LocalDate.of(2026, 11, 4));
  }

  @Test
  void aSlotTypeMustBeAnExactName() {
    assertBadRequest(() -> PlanParams.slotType(null));
    assertBadRequest(() -> PlanParams.slotType("LUNCHES"));
    assertBadRequest(() -> PlanParams.slotType("lunch"));
    assertThat(PlanParams.slotType("LUNCH")).isEqualTo(SlotType.LUNCH);
  }

  @Test
  void anIdMustBeAWholeNumber() {
    assertBadRequest(() -> PlanParams.id(null));
    assertBadRequest(() -> PlanParams.id("a"));
    assertThat(PlanParams.id("7")).isEqualTo(7L);
  }
}
