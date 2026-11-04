package hu.lasterbergamot.mealmagician.assistant.internal;

/**
 * What {@link ModelGateway#complete} returns: the model's text and what the call used. It is
 * deliberately small; later issues add structured output and tool calls next to it.
 *
 * @param text the model's answer; empty (never {@code null}) when the model returned nothing, for
 *     example when its safety filter blocked the answer
 * @param inputTokens tokens sent, as the provider counted them
 * @param outputTokens tokens billed as output: the answer plus the model's "thinking" tokens
 * @param costMicroUsd what this call cost, in micro-USD (1 USD = 1 000 000), rounded up
 */
public record ModelReply(String text, long inputTokens, long outputTokens, long costMicroUsd) {}
