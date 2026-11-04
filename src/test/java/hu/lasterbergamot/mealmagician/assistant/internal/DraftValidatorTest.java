package hu.lasterbergamot.mealmagician.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import hu.lasterbergamot.mealmagician.assistant.internal.DraftProposal.Idea;
import hu.lasterbergamot.mealmagician.assistant.internal.DraftProposal.ProposedItem;
import hu.lasterbergamot.mealmagician.planning.SlotType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The checks on the model's proposal: ids, dates, the idea cap and the text cleaning. */
class DraftValidatorTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  private static final Set<Long> ACTIVE = Set.of(1L, 2L, 3L);

  private static ProposedItem meal(int dayOffset, SlotType type, Long id) {
    return new ProposedItem(MONDAY.plusDays(dayOffset), type, id, null);
  }

  private static ProposedItem idea(int dayOffset, String name) {
    return new ProposedItem(
        MONDAY.plusDays(dayOffset),
        SlotType.DINNER,
        null,
        new Idea(
            name,
            "  because\nit is   good ",
            List.of("quick", "Quick", " soup "),
            List.of("rice")));
  }

  private static DraftValidator.Result check(String summary, ProposedItem... items) {
    return DraftValidator.check(new DraftProposal(summary, Arrays.asList(items)), MONDAY, ACTIVE);
  }

  @Test
  void libraryItemsKeepTheirOrderAndGetPlacesPerDayAndSlotType() {
    var result =
        check(
            "A week",
            meal(0, SlotType.LUNCH, 1L),
            meal(0, SlotType.LUNCH, 2L),
            meal(0, SlotType.DINNER, 3L));

    assertThat(result.items()).extracting(DraftValidator.Item::position).containsExactly(0, 1, 0);
    assertThat(result.items()).extracting(DraftValidator.Item::mealId).containsExactly(1L, 2L, 3L);
    assertThat(result.invalidIds()).isEmpty();
    assertThat(result.complaint()).isNull();
    assertThat(result.summary()).isEqualTo("A week");
  }

  @Test
  void anIdNotInTheLibraryIsDroppedCountedOnceAndNamedInTheComplaint() {
    var result =
        check(
            null,
            meal(0, SlotType.LUNCH, 999L),
            meal(1, SlotType.LUNCH, 999L),
            meal(2, SlotType.LUNCH, 1L));

    assertThat(result.items()).hasSize(1);
    assertThat(result.invalidIds()).containsExactly(999L);
    assertThat(result.complaint()).contains("[999]");
    assertThat(result.summary()).isNull();
  }

  @Test
  void aDraftWithNothingUsableComplainsThatItIsEmpty() {
    var result = check("x");

    assertThat(result.complaint()).contains("no usable items");
  }

  @Test
  void nullItemsMissingFieldsAndDatesOutsideTheWeekAreDropped() {
    List<ProposedItem> bad = new ArrayList<>();
    bad.add(null);
    bad.add(new ProposedItem(null, SlotType.LUNCH, 1L, null));
    bad.add(new ProposedItem(MONDAY, null, 1L, null));
    bad.add(new ProposedItem(MONDAY.minusDays(1), SlotType.LUNCH, 1L, null));
    bad.add(new ProposedItem(MONDAY.plusDays(7), SlotType.LUNCH, 1L, null));
    bad.add(new ProposedItem(MONDAY, SlotType.LUNCH, null, null));
    bad.add(meal(6, SlotType.SNACK, 1L));

    var result = DraftValidator.check(new DraftProposal("s", bad), MONDAY, ACTIVE);

    assertThat(result.items())
        .singleElement()
        .extracting(DraftValidator.Item::mealId)
        .isEqualTo(1L);
    assertThat(result.invalidIds()).isEmpty();
  }

  @Test
  void aProposalWithNoItemListIsEmpty() {
    var result = DraftValidator.check(new DraftProposal("s", null), MONDAY, ACTIVE);

    assertThat(result.items()).isEmpty();
  }

  @Test
  void sixIdeasAreCappedAtFive() {
    var result =
        check(
            "s",
            idea(0, "a"),
            idea(1, "b"),
            idea(2, "c"),
            idea(3, "d"),
            idea(4, "e"),
            idea(5, "f"));

    assertThat(result.items()).hasSize(5);
    assertThat(result.items())
        .extracting(DraftValidator.Item::name)
        .containsExactly("a", "b", "c", "d", "e");
  }

  @Test
  void anIdeaWithoutANameIsDroppedAndTheTextsAreCleaned() {
    var result = check("s", idea(0, "   "), idea(1, "  Fish\nsoup  "));

    assertThat(result.items()).hasSize(1);
    var kept = result.items().getFirst();
    assertThat(kept.name()).isEqualTo("Fish soup");
    assertThat(kept.reason()).isEqualTo("because it is good");
    assertThat(kept.tags()).isEqualTo("quick\nsoup");
    assertThat(kept.ingredients()).isEqualTo("rice");
    assertThat(kept.mealId()).isNull();
  }

  @Test
  void anIdeaWithoutAReasonOrListsStoresNulls() {
    var item = new ProposedItem(MONDAY, SlotType.DINNER, null, new Idea("Soup", " ", null, null));

    var result = DraftValidator.check(new DraftProposal("s", List.of(item)), MONDAY, ACTIVE);

    var kept = result.items().getFirst();
    assertThat(kept.reason()).isNull();
    assertThat(kept.tags()).isNull();
    assertThat(kept.ingredients()).isNull();
  }

  @Test
  void aLibraryMealIdWinsOverAnIdeaOnTheSameItem() {
    var item = new ProposedItem(MONDAY, SlotType.LUNCH, 1L, new Idea("Soup", null, null, null));

    var result = DraftValidator.check(new DraftProposal("s", List.of(item)), MONDAY, ACTIVE);

    assertThat(result.items().getFirst().mealId()).isEqualTo(1L);
    assertThat(result.items().getFirst().name()).isNull();
  }

  @Test
  void cleanCollapsesWhitespaceAndCutsToTheLimit() {
    assertThat(DraftValidator.clean(null, 5)).isEmpty();
    assertThat(DraftValidator.clean("  a \n b  ", 10)).isEqualTo("a b");
    assertThat(DraftValidator.clean("abcdefgh", 5)).isEqualTo("abcde");
    assertThat(DraftValidator.clean("ab  defgh", 3)).isEqualTo("ab");
  }

  @Test
  void namesSkipBlankLongAndRepeatedAndStopAtTheCountAndTheColumnSize() {
    assertThat(DraftValidator.names(null, 10, 5, 100)).isNull();
    assertThat(DraftValidator.names(List.of(" ", "waytoolongname", "A", "a"), 10, 5, 100))
        .isEqualTo("A");
    assertThat(DraftValidator.names(List.of("a", "b", "c"), 10, 2, 100)).isEqualTo("a\nb");
    assertThat(DraftValidator.names(List.of("aaaa", "bbbb", "cccc"), 10, 5, 9))
        .isEqualTo("aaaa\nbbbb");
    assertThat(DraftValidator.names(List.of(), 10, 5, 100)).isNull();
  }

  @Test
  void splitIsTheInverseOfNames() {
    assertThat(DraftValidator.split(null)).isEmpty();
    assertThat(DraftValidator.split("a\nb")).containsExactly("a", "b");
  }
}
