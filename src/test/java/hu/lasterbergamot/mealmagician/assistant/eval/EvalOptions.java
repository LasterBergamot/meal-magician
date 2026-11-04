package hu.lasterbergamot.mealmagician.assistant.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command line of the eval runner, parsed.
 *
 * @param help whether {@code --help} was given; then nothing else is needed
 * @param models the chat model ids to compare, in order
 * @param runs how many times each case runs per model, to see the variance
 * @param cases the case ids to run, or empty for all
 * @param priceIn USD per million input tokens, by model id
 * @param priceOut USD per million output tokens, by model id
 * @param budgetUsd the most one model may spend in one eval; the runner stops that model when its
 *     runs add up to it
 */
public record EvalOptions(
    boolean help,
    List<String> models,
    int runs,
    List<String> cases,
    Map<String, BigDecimal> priceIn,
    Map<String, BigDecimal> priceOut,
    BigDecimal budgetUsd) {

  /** How many times each case runs when {@code --runs} is not given. */
  static final int DEFAULT_RUNS = 3;

  /** The spend limit per model when {@code --budget-usd} is not given. */
  static final BigDecimal DEFAULT_BUDGET_USD = new BigDecimal("2.00");

  /** A command line that cannot be used; the message says what to fix. */
  static final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UsageException(String message) {
      super(message);
    }
  }

  /**
   * Parses the arguments. Both {@code --runs 3} and {@code --runs=3} work.
   *
   * @param args the arguments after the program name
   * @return the options
   * @throws UsageException if an option is unknown, a value is missing or wrong, or {@code
   *     --models} is missing (unless {@code --help} is given)
   */
  static EvalOptions parse(String[] args) {
    boolean help = false;
    List<String> models = List.of();
    int runs = DEFAULT_RUNS;
    List<String> cases = List.of();
    Map<String, BigDecimal> priceIn = Map.of();
    Map<String, BigDecimal> priceOut = Map.of();
    BigDecimal budget = DEFAULT_BUDGET_USD;
    List<String> rest = new ArrayList<>(Arrays.asList(args));
    while (!rest.isEmpty()) {
      String arg = rest.removeFirst();
      String name = arg;
      String inline = null;
      int eq = arg.indexOf('=');
      if (arg.startsWith("--") && eq > 0) {
        name = arg.substring(0, eq);
        inline = arg.substring(eq + 1);
      }
      if (name.equals("--help") || name.equals("-h")) {
        help = true;
        continue;
      }
      if (!List.of("--models", "--runs", "--cases", "--price-in", "--price-out", "--budget-usd")
          .contains(name)) {
        throw new UsageException("unknown option " + arg);
      }
      String value = inline;
      if (value == null) {
        if (rest.isEmpty()) {
          throw new UsageException(name + " needs a value");
        }
        value = rest.removeFirst();
      }
      switch (name) {
        case "--models" -> models = list(name, value);
        case "--cases" -> cases = list(name, value);
        case "--runs" -> runs = positive(name, value);
        case "--price-in" -> priceIn = prices(name, value);
        case "--price-out" -> priceOut = prices(name, value);
        default -> budget = money(name, value);
      }
    }
    if (!help && models.isEmpty()) {
      throw new UsageException("--models is required");
    }
    return new EvalOptions(help, models, runs, cases, priceIn, priceOut, budget);
  }

  private static List<String> list(String name, String value) {
    List<String> items =
        Arrays.stream(value.split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    if (items.isEmpty()) {
      throw new UsageException(name + " needs at least one value");
    }
    return items;
  }

  private static int positive(String name, String value) {
    try {
      int n = Integer.parseInt(value.strip());
      if (n >= 1) {
        return n;
      }
    } catch (NumberFormatException notANumber) {
      // Falls through to the message below.
    }
    throw new UsageException(name + " must be a whole number of 1 or more, not \"" + value + "\"");
  }

  private static BigDecimal money(String name, String value) {
    try {
      BigDecimal amount = new BigDecimal(value.strip());
      if (amount.signum() > 0) {
        return amount;
      }
    } catch (NumberFormatException notANumber) {
      // Falls through to the message below.
    }
    throw new UsageException(name + " must be a positive number, not \"" + value + "\"");
  }

  private static Map<String, BigDecimal> prices(String name, String value) {
    Map<String, BigDecimal> prices = new LinkedHashMap<>();
    for (String entry : list(name, value)) {
      int eq = entry.indexOf('=');
      if (eq <= 0) {
        throw new UsageException(name + " takes model=price pairs, not \"" + entry + "\"");
      }
      prices.put(entry.substring(0, eq).strip(), money(name, entry.substring(eq + 1)));
    }
    return prices;
  }
}
