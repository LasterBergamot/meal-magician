package hu.lasterbergamot.mealmagician.assistant.internal;

import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the model's proposal against the real catalog and the week, and cleans every text to the
 * size of its column. The model is not trusted: an id it invented, a date outside the week or an
 * idea with no name never reaches storage. Pure functions on plain values, so it needs no database
 * to test.
 */
final class DraftValidator {

  /** The most ideas one draft keeps (PLAN.md D32). */
  static final int MAX_IDEAS = 5;

  static final int SUMMARY_MAX = 1000;
  static final int NAME_MAX = 100;
  static final int REASON_MAX = 300;
  static final int TAGS_MAX = 300;
  static final int INGREDIENTS_MAX = 500;

  /**
   * A tag is at most this long, the catalog's own limit; a longer one would fail "add to library".
   */
  static final int TAG_MAX = 50;

  /** An ingredient name is at most this long, the catalog's own limit. */
  static final int INGREDIENT_MAX = 60;

  /** A meal has at most this many ingredients in the catalog. */
  static final int INGREDIENTS_PER_MEAL = 30;

  static final int TAGS_PER_IDEA = 5;
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private DraftValidator() {}

  /**
   * A checked proposal, ready to store.
   *
   * @param summary the cleaned summary, or {@code null}
   * @param items the items that passed, in the model's order, with their places numbered
   * @param invalidIds the library meal ids the model named that are not active meals of the
   *     household, without repeats, in the order first named
   */
  record Result(String summary, List<Item> items, List<Long> invalidIds) {

    /**
     * What to tell the model when its answer needs another try.
     *
     * @return a sentence for the model, or {@code null} when the answer is usable as it is
     */
    String complaint() {
      if (!invalidIds.isEmpty()) {
        return "These meal ids are not in the library: "
            + invalidIds
            + ". Use only ids returned by the tools, or propose an idea instead.";
      }
      if (items.isEmpty()) {
        return "The draft has no usable items. Propose meals for the days of the week.";
      }
      return null;
    }
  }

  /**
   * One item that passed the checks.
   *
   * @param date the day, inside the week
   * @param slotType the slot type
   * @param position the place in the day and slot type, counted from 0
   * @param mealId the library meal, or {@code null} for an idea
   * @param name the idea's name; {@code null} for a library item
   * @param reason the idea's reason, or {@code null}
   * @param tags the idea's tags joined with a line feed, or {@code null}
   * @param ingredients the idea's ingredients joined with a line feed, or {@code null}
   */
  record Item(
      LocalDate date,
      SlotType slotType,
      int position,
      Long mealId,
      String name,
      String reason,
      String tags,
      String ingredients) {}

  /**
   * Checks a proposal.
   *
   * @param proposal what the model answered
   * @param monday the Monday of the week being planned
   * @param activeMealIds the ids of the household's non-archived meals
   * @return the items that passed, the invalid ids and the cleaned summary
   */
  static Result check(DraftProposal proposal, LocalDate monday, Set<Long> activeMealIds) {
    LocalDate sunday = monday.plusDays(6);
    Set<Long> invalid = new LinkedHashSet<>();
    List<Item> items = new ArrayList<>();
    Map<String, Integer> next = new HashMap<>();
    int ideas = 0;
    List<DraftProposal.ProposedItem> proposed =
        proposal.items() == null ? List.of() : proposal.items();
    for (DraftProposal.ProposedItem item : proposed) {
      if (item == null
          || item.date() == null
          || item.date().isBefore(monday)
          || item.date().isAfter(sunday)
          || item.slotType() == null) {
        continue;
      }
      String key = item.date() + "/" + item.slotType();
      if (item.mealId() != null) {
        if (!activeMealIds.contains(item.mealId())) {
          invalid.add(item.mealId());
          continue;
        }
        int position = next.merge(key, 1, Integer::sum) - 1;
        items.add(
            new Item(
                item.date(), item.slotType(), position, item.mealId(), null, null, null, null));
      } else if (item.idea() != null) {
        String name = clean(item.idea().name(), NAME_MAX);
        if (name.isEmpty() || ideas >= MAX_IDEAS) {
          continue;
        }
        ideas++;
        int position = next.merge(key, 1, Integer::sum) - 1;
        String reason = clean(item.idea().reason(), REASON_MAX);
        items.add(
            new Item(
                item.date(),
                item.slotType(),
                position,
                null,
                name,
                reason.isEmpty() ? null : reason,
                names(item.idea().tags(), TAG_MAX, TAGS_PER_IDEA, TAGS_MAX),
                names(
                    item.idea().ingredients(),
                    INGREDIENT_MAX,
                    INGREDIENTS_PER_MEAL,
                    INGREDIENTS_MAX)));
      }
    }
    String summary = clean(proposal.summary(), SUMMARY_MAX);
    return new Result(summary.isEmpty() ? null : summary, List.copyOf(items), List.copyOf(invalid));
  }

  /**
   * Collapses all whitespace (line feeds included) to single spaces, trims, and cuts to a length.
   *
   * @param text the text, may be {@code null}
   * @param max the longest result
   * @return the cleaned text; empty for {@code null} or blank input
   */
  static String clean(String text, int max) {
    if (text == null) {
      return "";
    }
    String collapsed = WHITESPACE.matcher(text).replaceAll(" ").strip();
    return collapsed.length() <= max ? collapsed : collapsed.substring(0, max).strip();
  }

  /**
   * Cleans a list of names and joins them with a line feed. A name that is blank, too long for the
   * catalog or a repeat (ignoring case) is skipped, and the list stops at the item count or the
   * column size.
   *
   * @param raw the names, may be {@code null}
   * @param itemMax the longest name the catalog accepts
   * @param countMax the most names kept
   * @param totalMax the size of the column
   * @return the joined names, or {@code null} when none are left
   */
  static String names(List<String> raw, int itemMax, int countMax, int totalMax) {
    if (raw == null) {
      return null;
    }
    List<String> kept = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    int length = 0;
    for (String candidate : raw) {
      String name = clean(candidate, itemMax + 1);
      if (name.isEmpty() || name.length() > itemMax || !seen.add(name.toLowerCase(Locale.ROOT))) {
        continue;
      }
      int added =
          length + name.length() + (kept.isEmpty() ? 0 : DraftItemEntity.SEPARATOR.length());
      if (kept.size() >= countMax || added > totalMax) {
        break;
      }
      kept.add(name);
      length = added;
    }
    return kept.isEmpty() ? null : String.join(DraftItemEntity.SEPARATOR, kept);
  }

  /**
   * Splits stored names back into a list.
   *
   * @param joined the stored text, may be {@code null}
   * @return the names; empty for {@code null}
   */
  static List<String> split(String joined) {
    return joined == null ? List.of() : List.of(joined.split(DraftItemEntity.SEPARATOR));
  }
}
