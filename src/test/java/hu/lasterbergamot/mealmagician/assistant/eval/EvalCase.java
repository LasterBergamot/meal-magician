package hu.lasterbergamot.mealmagician.assistant.eval;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * One case of the assistant eval: a request, the day it is asked, the things to set up first (an
 * occasion, an extra meal) and the deterministic checks the answer must pass. The cases live in
 * {@code src/test/resources/eval/cases.json} and are made of invented data only.
 *
 * @param id a short name used on the command line ({@code --cases}) and in the report
 * @param kind what is asked: {@code week} (a week draft), {@code slot} (one slot), {@code menu} (an
 *     occasion's menu) or {@code chat} (one chat message)
 * @param locale {@code hu} or {@code en}: the language of the member, which the answer must be
 *     written in
 * @param date "today" for the assistant while the case runs (the clock is set to it), and the day
 *     whose week is planned
 * @param request what the member typed; {@code null} for {@code slot} and {@code menu} cases
 * @param baseline the id of the case whose answer this one is compared with ({@code lessMeat}),
 *     else {@code null}
 * @param allowAdjacentRepeat whether the same meal on two neighbouring days is acceptable here
 * @param occasions occasions to create before the case runs and delete after it
 * @param extraMeals meals to add to the library for this case only (archived afterwards)
 * @param slotDate the day of the slot, for {@code slot} cases
 * @param slotType the slot type name, for {@code slot} cases
 * @param checks the checks to apply, in order
 */
public record EvalCase(
    String id,
    String kind,
    String locale,
    LocalDate date,
    String request,
    String baseline,
    boolean allowAdjacentRepeat,
    List<CaseOccasion> occasions,
    List<ExtraMeal> extraMeals,
    LocalDate slotDate,
    String slotType,
    List<CheckSpec> checks) {

  /**
   * Replaces the lists a case leaves out of the JSON with empty ones, so the rest of the code never
   * meets {@code null}.
   */
  public EvalCase {
    occasions = occasions == null ? List.of() : List.copyOf(occasions);
    extraMeals = extraMeals == null ? List.of() : List.copyOf(extraMeals);
    checks = checks == null ? List.of() : List.copyOf(checks);
  }

  /** The kinds of case the harness can run. */
  static final Set<String> KINDS = Set.of("week", "slot", "menu", "chat");

  /** Where the cases are read from, on the test classpath. */
  static final String RESOURCE = "/eval/cases.json";

  // A flag left out of the file (allowAdjacentRepeat, archived) means false.
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

  /**
   * An occasion made for one case.
   *
   * @param name the occasion's name
   * @param date its date
   * @param type its type
   */
  public record CaseOccasion(String name, LocalDate date, OccasionType type) {}

  /**
   * A meal added to the library for one case, for example one whose name tries to give the model
   * orders.
   *
   * @param key a name the checks refer to it by
   * @param name the meal's name
   * @param tags its tags
   * @param ingredients its ingredients
   */
  public record ExtraMeal(String key, String name, List<String> tags, List<String> ingredients) {}

  /**
   * One check to apply, with the settings only some checks use.
   *
   * @param check the check's name (see {@link Checks#NAMES})
   * @param day a weekday name such as {@code THURSDAY} ({@code quickOn})
   * @param tag a tag name ({@code quickOn})
   * @param words ingredient words ({@code usesIngredient}, {@code forbiddenIngredients})
   * @param meal the key of an extra meal ({@code maxUses})
   * @param max a limit ({@code maxUses})
   */
  public record CheckSpec(
      String check, String day, String tag, List<String> words, String meal, Integer max) {}

  /**
   * Lists the words and tables the checks look things up in. They are written by hand and used by
   * the checks only; the product never reads them.
   *
   * @param meat what counts as a meat meal
   * @param outOfSeason ingredient words that are out of season, by month number as text
   * @param stopWords common words of each language, by language code ({@code hu}, {@code en})
   */
  public record Reference(
      Meat meat, Map<String, List<String>> outOfSeason, Map<String, List<String>> stopWords) {}

  /**
   * What counts as meat: a meal with the tag, or with an ingredient that contains one of the words.
   * Fish does not count.
   *
   * @param tag the tag that marks a meat meal
   * @param words ingredient words that mean meat
   */
  public record Meat(String tag, List<String> words) {}

  /**
   * The whole file: the reference lists and the cases.
   *
   * @param reference the lookup tables
   * @param cases the cases, in the order they run
   */
  public record Suite(Reference reference, List<EvalCase> cases) {

    /**
     * Finds a case by id.
     *
     * @param id the case id
     * @return the case
     * @throws IllegalArgumentException if there is no such case
     */
    public EvalCase get(String id) {
      return cases.stream()
          .filter(c -> c.id().equals(id))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("no eval case named " + id));
    }
  }

  /**
   * The locale of this case's member.
   *
   * @return the locale for {@link #locale}
   */
  public Locale asLocale() {
    return Locale.forLanguageTag(locale);
  }

  /**
   * Reads the cases from the test classpath.
   *
   * @return the suite
   * @throws IllegalStateException if the file is missing, is not valid or breaks a rule of {@link
   *     #validate}
   */
  public static Suite load() {
    try (InputStream in = EvalCase.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the test classpath");
      }
      return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + RESOURCE, e);
    }
  }

  /**
   * Parses and validates a suite from JSON text.
   *
   * @param json the content of a {@code cases.json}
   * @return the suite
   * @throws IllegalStateException if the text is not valid or breaks a rule of {@link #validate}
   */
  static Suite parse(String json) {
    Suite suite;
    try {
      suite = JSON.readValue(json, Suite.class);
    } catch (RuntimeException e) {
      throw new IllegalStateException("cases.json is not valid: " + e.getMessage(), e);
    }
    validate(suite);
    return suite;
  }

  /**
   * Checks the rules the JSON format cannot: unique ids, a known kind, a known check name for the
   * kind, and a baseline that exists and comes first.
   *
   * @param suite the suite to check
   * @throws IllegalStateException on the first rule that is broken
   */
  static void validate(Suite suite) {
    Set<String> seen = new HashSet<>();
    for (EvalCase c : suite.cases()) {
      if (!seen.add(c.id())) {
        throw new IllegalStateException("duplicate eval case id " + c.id());
      }
      if (!KINDS.contains(c.kind())) {
        throw new IllegalStateException("case " + c.id() + " has an unknown kind " + c.kind());
      }
      for (CheckSpec spec : c.checks()) {
        if (!Checks.allowedFor(c.kind()).contains(spec.check())) {
          throw new IllegalStateException(
              "case " + c.id() + " (" + c.kind() + ") cannot use the check " + spec.check());
        }
      }
      if (c.baseline() != null && !seen.contains(c.baseline())) {
        throw new IllegalStateException(
            "case " + c.id() + " needs its baseline " + c.baseline() + " earlier in the file");
      }
    }
  }
}
