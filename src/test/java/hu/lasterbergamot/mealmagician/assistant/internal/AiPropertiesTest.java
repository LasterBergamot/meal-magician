package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiPropertiesTest {

  private static AiProperties prices(String input, String output) {
    return new AiProperties(
        true,
        new BigDecimal("5.00"),
        new BigDecimal(input),
        new BigDecimal(output),
        768,
        Duration.ofSeconds(60));
  }

  @Test
  void defaultsAreOffFiveDollarsNoPricesAndSixtySeconds() {
    var bound =
        new Binder(new MapConfigurationPropertySource(Map.of()))
            .bindOrCreate("mm.ai", AiProperties.class);

    assertThat(bound.enabled()).isFalse();
    assertThat(bound.monthlyCapUsd()).isEqualByComparingTo("5.00");
    assertThat(bound.priceInputPerMillionUsd()).isNull();
    assertThat(bound.priceOutputPerMillionUsd()).isNull();
    assertThat(bound.embeddingDimensions()).isEqualTo(768);
    assertThat(bound.timeout()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void capIsConvertedToMicroUsd() {
    assertThat(prices("1", "1").capMicroUsd()).isEqualTo(5_000_000L);
  }

  @Test
  void costIsInputPlusOutputAtTheirOwnPrices() {
    // 1 000 x 1.65 + 500 x 9.90 = 1 650 + 4 950 micro-USD
    assertThat(prices("1.65", "9.90").costMicroUsd(1_000, 500)).isEqualTo(6_600L);
  }

  @Test
  void costIsRoundedUpNeverDown() {
    // 1 token at 0.3333 USD per million is 0.3333 micro-USD: counted as 1, not 0.
    assertThat(prices("0.3333", "0").costMicroUsd(1, 0)).isEqualTo(1L);
    // exactly 3 micro-USD stays 3
    assertThat(prices("1.5", "0").costMicroUsd(2, 0)).isEqualTo(3L);
    assertThat(prices("1.5", "0").costMicroUsd(0, 0)).isZero();
  }

  @Test
  void missingPricesAreAnErrorNotAFreeCall() {
    var noPrices =
        new AiProperties(true, new BigDecimal("5"), null, null, 768, Duration.ofSeconds(1));
    assertThatThrownBy(() -> noPrices.costMicroUsd(1, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("price-input-per-million-usd");
    var onlyInput =
        new AiProperties(
            true, new BigDecimal("5"), BigDecimal.ONE, null, 768, Duration.ofSeconds(1));
    assertThatThrownBy(() -> onlyInput.costMicroUsd(1, 1))
        .isInstanceOf(IllegalStateException.class);
  }
}
