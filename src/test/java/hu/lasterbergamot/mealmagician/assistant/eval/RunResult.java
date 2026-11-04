package hu.lasterbergamot.mealmagician.assistant.eval;

import java.util.List;

/**
 * What one run of one case against one model produced: the verdicts of the checks, what the call
 * cost, and how long it took.
 *
 * @param model the chat model id
 * @param caseId the case
 * @param run the run number, from 1
 * @param checks the verdict of each check; empty when the assistant gave no answer
 * @param error why there was no answer (the kind of failure only, never the model's text), else
 *     {@code null}
 * @param inputTokens tokens sent, as the spend ledger counted them
 * @param outputTokens tokens received, thinking tokens included
 * @param costMicroUsd the cost in micro-USD, from the ledger at this model's price
 * @param latencyMillis wall-clock time of the whole assistant call, including its retry
 * @param invalidIds whether the model named a meal id that does not exist (in the final answer, or
 *     in answers the assistant refused)
 */
public record RunResult(
    String model,
    String caseId,
    int run,
    List<Checks.Named> checks,
    String error,
    long inputTokens,
    long outputTokens,
    long costMicroUsd,
    long latencyMillis,
    boolean invalidIds) {

  /**
   * Whether the run is a pass: the assistant answered and every check passed.
   *
   * @return {@code true} for a pass
   */
  public boolean passed() {
    return error == null && checks.stream().allMatch(c -> c.result().pass());
  }
}
