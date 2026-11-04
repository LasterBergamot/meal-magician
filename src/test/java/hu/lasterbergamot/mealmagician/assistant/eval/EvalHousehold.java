package hu.lasterbergamot.mealmagician.assistant.eval;

import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The invented household the eval runs against, as written in {@code
 * src/test/resources/eval/household.json}: its members, its meals and a few weeks of planned and
 * rated history. It is only data; {@link EvalHarness} loads it through the real services.
 *
 * @param name the household's name
 * @param members the invented members
 * @param meals the meals of the library, some archived
 * @param history the planned meals of the three weeks before a case's week, with ratings
 */
public record EvalHousehold(
    String name, List<Member> members, List<Meal> meals, List<Planned> history) {

  /** Where the household is read from, on the test classpath. */
  static final String RESOURCE = "/eval/household.json";

  // A flag left out of the file (allowAdjacentRepeat, archived) means false.
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

  /**
   * An invented member.
   *
   * @param name the display name
   */
  public record Member(String name) {}

  /**
   * A meal of the library.
   *
   * @param key a short name the history refers to it by
   * @param name the meal's name
   * @param tags its tags
   * @param ingredients its ingredients
   * @param occasionTypes the occasion types it suits, may be left out
   * @param archived whether to archive it after it is created
   */
  public record Meal(
      String key,
      String name,
      List<String> tags,
      List<String> ingredients,
      List<OccasionType> occasionTypes,
      boolean archived) {

    /** Replaces a left-out list with an empty one. */
    public Meal {
      tags = tags == null ? List.of() : List.copyOf(tags);
      ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
      occasionTypes = occasionTypes == null ? List.of() : List.copyOf(occasionTypes);
    }
  }

  /**
   * One planned meal in the history.
   *
   * @param weekOffset how many weeks before the case's week, negative ({@code -1} is last week)
   * @param day the day of the week, 0 for Monday
   * @param slot the slot type name
   * @param meal the key of the meal
   * @param ratings what members thought of it, may be left out
   */
  public record Planned(int weekOffset, int day, String slot, String meal, List<Rating> ratings) {

    /** Replaces a left-out list with an empty one. */
    public Planned {
      ratings = ratings == null ? List.of() : List.copyOf(ratings);
    }
  }

  /**
   * One member's rating of a planned meal.
   *
   * @param member the index into {@link EvalHousehold#members}
   * @param thumb {@code UP} or {@code DOWN}
   * @param stars one to five, may be {@code null}
   * @param comment a comment, may be {@code null}
   */
  public record Rating(int member, String thumb, Integer stars, String comment) {}

  /**
   * Reads the household from the test classpath.
   *
   * @return the household
   * @throws IllegalStateException if the file is missing or invalid
   */
  public static EvalHousehold load() {
    try (InputStream in = EvalHousehold.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the test classpath");
      }
      return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + RESOURCE, e);
    }
  }

  /**
   * Parses a household from JSON text and checks that its history points at meals and members that
   * exist.
   *
   * @param json the content of a {@code household.json}
   * @return the household
   * @throws IllegalStateException if the text is invalid, a key is used twice, or the history names
   *     an unknown meal or member
   */
  static EvalHousehold parse(String json) {
    EvalHousehold household;
    try {
      household = JSON.readValue(json, EvalHousehold.class);
    } catch (RuntimeException e) {
      throw new IllegalStateException("household.json is not valid: " + e.getMessage(), e);
    }
    Set<String> keys = new HashSet<>();
    for (Meal meal : household.meals()) {
      if (!keys.add(meal.key())) {
        throw new IllegalStateException("duplicate meal key " + meal.key());
      }
    }
    for (Planned planned : household.history()) {
      if (!keys.contains(planned.meal())) {
        throw new IllegalStateException("history names the unknown meal " + planned.meal());
      }
      for (Rating rating : planned.ratings()) {
        if (rating.member() < 0 || rating.member() >= household.members().size()) {
          throw new IllegalStateException("history names the unknown member " + rating.member());
        }
      }
    }
    return household;
  }
}
