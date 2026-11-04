package hu.lasterbergamot.mealmagician.assistant.eval;

import hu.lasterbergamot.mealmagician.assistant.DraftItemView;
import hu.lasterbergamot.mealmagician.assistant.DraftStatus;
import hu.lasterbergamot.mealmagician.assistant.DraftView;
import hu.lasterbergamot.mealmagician.catalog.OccasionType;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Small hand-made drafts and libraries for the tests of the checks. Nothing here is real data. */
final class EvalSamples {

  static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

  /** Ids 1 to 6 are active, 7 is archived. */
  static final Fixture LIBRARY =
      new Fixture(
          List.of(
              meal(
                  1,
                  "Gulyásleves",
                  List.of("leves", "húsos"),
                  List.of("marhahús", "burgonya"),
                  Set.of()),
              meal(
                  2,
                  "Tojásos lecsó",
                  List.of("vegetáriánus", "gyors"),
                  List.of("paprika", "tojás"),
                  Set.of()),
              meal(
                  3,
                  "Tonhalas tészta",
                  List.of("halas", "gyors"),
                  List.of("tészta", "tonhal"),
                  Set.of()),
              meal(
                  4,
                  "Csokoládétorta",
                  List.of("édes"),
                  List.of("csokoládé", "liszt"),
                  Set.of(OccasionType.BIRTHDAY)),
              meal(5, "Gombaleves", List.of("leves"), List.of("gomba", "tejföl"), Set.of()),
              meal(
                  6,
                  "Görögdinnyés saláta",
                  List.of("nyári"),
                  List.of("görögdinnye", "feta"),
                  Set.of()),
              new Fixture.Meal(7, "Régi leves", List.of(), List.of(), Set.of(), true)));

  private EvalSamples() {}

  static Fixture.Meal meal(
      long id, String name, List<String> tags, List<String> ingredients, Set<OccasionType> types) {
    return new Fixture.Meal(id, name, tags, ingredients, types, false);
  }

  /** A library item on a day. */
  static DraftItemView item(int dayOffset, SlotType slot, long mealId) {
    String name = LIBRARY.find(mealId).map(Fixture.Meal::name).orElse("?");
    return new DraftItemView(
        mealId * 100 + dayOffset,
        MONDAY.plusDays(dayOffset),
        slot,
        0,
        mealId,
        name,
        null,
        null,
        null,
        null);
  }

  /** An idea on a day. */
  static DraftItemView idea(int dayOffset, SlotType slot, String name, List<String> tags) {
    return new DraftItemView(
        900 + dayOffset,
        MONDAY.plusDays(dayOffset),
        slot,
        0,
        null,
        null,
        name,
        "Mert jó.",
        tags,
        List.of("tojás"));
  }

  static DraftView draft(String summary, DraftItemView... items) {
    return new DraftView(
        1, MONDAY, DraftStatus.OPEN, "kérés", summary, List.of(items), Instant.EPOCH);
  }

  /** A draft with {@code meaty} meals of meat and {@code total - meaty} of vegetables. */
  static DraftView withMeat(int meaty, int total) {
    List<DraftItemView> items = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      items.add(item(i % 7, i < 7 ? SlotType.LUNCH : SlotType.DINNER, i < meaty ? 1 : 2));
    }
    return draft("Egy hét.", items.toArray(DraftItemView[]::new));
  }
}
