package hu.lasterbergamot.mealmagician.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.lasterbergamot.mealmagician.assistant.eval.EvalOptions.UsageException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalOptionsTest {

  @Test
  void modelsAreRequired() {
    assertThatThrownBy(() -> EvalOptions.parse(new String[] {}))
        .isInstanceOf(UsageException.class)
        .hasMessageContaining("--models is required");
  }

  @Test
  void helpNeedsNothingElse() {
    assertThat(EvalOptions.parse(new String[] {"--help"}).help()).isTrue();
    assertThat(EvalOptions.parse(new String[] {"-h"}).help()).isTrue();
  }

  @Test
  void defaultsAreThreeRunsAndATwoDollarLimit() {
    EvalOptions options = EvalOptions.parse(new String[] {"--models", "a,b"});

    assertThat(options.models()).containsExactly("a", "b");
    assertThat(options.runs()).isEqualTo(3);
    assertThat(options.cases()).isEmpty();
    assertThat(options.budgetUsd()).isEqualByComparingTo("2.00");
  }

  @Test
  void parsesEveryOptionInBothSpellings() {
    EvalOptions options =
        EvalOptions.parse(
            new String[] {
              "--models=a, b ,a",
              "--runs",
              "5",
              "--cases=plain-week,less-meat",
              "--price-in",
              "a=1.65,b=0.40",
              "--price-out=a=9.90,b=2.5",
              "--budget-usd",
              "0.50"
            });

    assertThat(options.models()).containsExactly("a", "b");
    assertThat(options.runs()).isEqualTo(5);
    assertThat(options.cases()).containsExactly("plain-week", "less-meat");
    assertThat(options.priceIn())
        .containsEntry("a", new BigDecimal("1.65"))
        .containsEntry("b", new BigDecimal("0.40"));
    assertThat(options.priceOut()).containsEntry("a", new BigDecimal("9.90"));
    assertThat(options.budgetUsd()).isEqualByComparingTo("0.5");
  }

  @Test
  void refusesWhatItCannotUse() {
    for (String[] bad :
        List.of(
            new String[] {"--models", "a", "--bogus"},
            new String[] {"--models"},
            new String[] {"--models", ","},
            new String[] {"--models", "a", "--runs", "0"},
            new String[] {"--models", "a", "--runs", "many"},
            new String[] {"--models", "a", "--budget-usd", "-1"},
            new String[] {"--models", "a", "--budget-usd", "lots"},
            new String[] {"--models", "a", "--price-in", "1.0"},
            new String[] {"--models", "a", "--price-in", "a=free"})) {
      assertThatThrownBy(() -> EvalOptions.parse(bad))
          .as(String.join(" ", bad))
          .isInstanceOf(UsageException.class);
    }
  }
}
