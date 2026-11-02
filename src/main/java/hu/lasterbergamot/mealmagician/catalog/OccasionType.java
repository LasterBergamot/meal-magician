package hu.lasterbergamot.mealmagician.catalog;

/**
 * The kinds of dated occasion a meal can suit: a birthday cake, a Christmas roast. A meal lists the
 * types it suits, and Phase 4's occasions (a dated event with its own menu) carry one type each, so
 * "which meals fit this occasion" is a lookup by type.
 *
 * <p><b>Why the catalog owns this enum.</b> The {@code planning} module already depends on {@code
 * catalog} (a planned slot points at a meal). If the enum lived in {@code planning}, the catalog
 * would need to import it to store a meal's types and Spring Modulith would see a cycle between the
 * two modules. Here the arrow stays one way: planning imports {@code OccasionType} from the catalog
 * when it builds occasions. The owner agreed to this on 2026-11-02 (issue #88).
 *
 * <p>The constant names are stored in the database as text and limited by a {@code CHECK} in
 * migration V8, so adding a constant needs a migration too. The words people read come from the
 * message bundles ({@code meal.occasion.*}), never from this enum.
 */
public enum OccasionType {
  /** A birthday. */
  BIRTHDAY,
  /** A holiday such as Christmas or Easter. */
  HOLIDAY,
  /** A party or a gathering. */
  PARTY,
  /** Any other occasion. */
  OTHER
}
