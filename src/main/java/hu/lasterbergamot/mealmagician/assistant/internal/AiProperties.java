package hu.lasterbergamot.mealmagician.assistant.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the assistant, under {@code mm.ai} (PLAN.md D32). The connection to Google (project,
 * location, model ids) is <em>not</em> here: it stays in Spring AI's own {@code
 * spring.ai.google.genai.*} settings, which {@code application.yml} fills from environment
 * variables.
 *
 * <p>Money is a {@link BigDecimal} in USD while it is configured, and a whole number of micro-USD
 * (1 USD = 1 000 000) everywhere else, so adding up never drifts. A price "per million tokens" in
 * USD is numerically the price of one token in micro-USD, which is why {@link #costMicroUsd} needs
 * no division.
 *
 * @param enabled the master switch; {@code false} (the default) means nothing in the module calls
 *     out, needs credentials or answers a request
 * @param monthlyCapUsd what one household may spend per calendar month; a household at or over it
 *     is refused before any call (default 5.00; {@code 0} blocks the assistant for everyone)
 * @param priceInputPerMillionUsd USD per million input tokens of the chosen chat model; required
 *     when enabled (no default, because it must match the model that was chosen)
 * @param priceOutputPerMillionUsd USD per million output tokens, "thinking" tokens included;
 *     required when enabled
 * @param embeddingDimensions the size of an embedding vector; the database column is fixed to it,
 *     so the gateway refuses a model that answers with another size (default 768)
 * @param timeout how long one call to the model may take before it is abandoned (default 60
 *     seconds)
 */
@ConfigurationProperties(prefix = "mm.ai")
public record AiProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("5.00") BigDecimal monthlyCapUsd,
    BigDecimal priceInputPerMillionUsd,
    BigDecimal priceOutputPerMillionUsd,
    @DefaultValue("768") int embeddingDimensions,
    @DefaultValue("60s") Duration timeout) {

  private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);

  /**
   * The monthly allowance in micro-USD.
   *
   * @return the cap, rounded up to a whole micro-USD
   */
  public long capMicroUsd() {
    return monthlyCapUsd.multiply(MICRO).setScale(0, RoundingMode.CEILING).longValueExact();
  }

  /**
   * What a call costs, in whole micro-USD, rounded <strong>up</strong> so the ledger can only ever
   * over-count, never under-count.
   *
   * @param inputTokens tokens sent
   * @param outputTokens tokens received, thinking tokens included
   * @return the cost in micro-USD
   * @throws IllegalStateException if a price is not configured
   */
  public long costMicroUsd(long inputTokens, long outputTokens) {
    if (priceInputPerMillionUsd == null || priceOutputPerMillionUsd == null) {
      throw new IllegalStateException(
          "mm.ai.price-input-per-million-usd and mm.ai.price-output-per-million-usd must be set");
    }
    return priceInputPerMillionUsd
        .multiply(BigDecimal.valueOf(inputTokens))
        .add(priceOutputPerMillionUsd.multiply(BigDecimal.valueOf(outputTokens)))
        .setScale(0, RoundingMode.CEILING)
        .longValueExact();
  }
}
